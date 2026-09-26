# Event Architecture (WP-02)

## 1. Shape

```
 HTTP ─▶ Payment API ─▶ CreatePaymentService ───────────── one local transaction ──────────────┐
                        INSERT payment, idempotency_record, payment_saga(AWAITING_RISK)        │
                        INSERT payment.outbox_event: PaymentCreated, AssessPaymentRisk          │ COMMIT
                                                                                               ▼
            payment.outbox_event ──(OutboxRelay: advisory lock, id order, acks=all)──▶ Kafka
                                                                                               │
  ┌───────────────── fraud.commands ─▶ Fraud (MongoDB) ─(sync publish, then ack)─▶ fraud.events ┤
  │                  funds.commands ─▶ Account funds (PG, inbox, FOR UPDATE) ─▶ outbox ─▶ funds.events ┤──▶ Ledger (follower)
  │             settlement.commands ─▶ Settlement (PG, resumable, rail) ─▶ outbox ─▶ settlement.events ┤
  │                                                                                            │
  └──── Payment saga (PaymentSagaListener → PaymentSagaService): inbox + saga + payment + outbox, one tx ◀┘
                               └── payment.events (public lifecycle stream)
```

Clean Architecture is preserved:
- The domain and application layers know nothing about Kafka, envelopes, offsets or contracts (ArchUnit `core_does_not_know_messaging`).
- Outbound: `PaymentEventPublisherPort`, `SagaCommandPort`, `FundsEventPublisherPort`, `SettlementEventPublisherPort` and `RiskDecisionPublisherPort` are implemented by `adapter.out.messaging` classes that write to the outbox (or, for Fraud, publish directly).
- Inbound: `@KafkaListener` classes live only in `adapter.in.messaging`. They validate, then call application ports.

## 2. Delivery semantics end to end

| Hop | Guarantee | Mechanism |
|---|---|---|
| Business change → outbox | Atomic | Same PostgreSQL transaction (`OutboxWriter` requires an active transaction) |
| Outbox → Kafka | At least once, per-aggregate order | Single relay per outbox (advisory lock), id order, stop at first failure, idempotent producer, `acks=all` |
| Kafka → consumer | At least once | Offset committed per record after the listener returns (AckMode.RECORD, auto-commit off) |
| Consumer effect | Effectively once | Inbox (eventId) + natural business keys + saga step guards (ADR-013) |
| Fraud (MongoDB) | At least once, stable decision | Assessment unique per payment. Persist, then publish synchronously, then ack. Redelivery re-publishes the stored decision. |

## 3. Identifiers (never overloaded)

| Id | Scope | Origin | Where |
|---|---|---|---|
| `traceId` / `spanId` | technical distributed trace | Micrometer/OTel | W3C `traceparent` header, captured at outbox write, restored by the relay, continued by listener observation; logs |
| `correlationId` | business request | HTTP `X-Correlation-Id` (or generated) | envelope + header + saga row + every log line; constant across the whole saga |
| `eventId` | one message | UUIDv7 at envelope creation | envelope + header; the inbox key; preserved on DLQ replay |
| `causationId` | "caused by" | the eventId being processed when the new message was produced | envelope; the chain of events |
| `sagaId` | workflow instance | the saga row | envelope + logs + `GET /payments/{id}/saga` |
| `paymentId` | aggregate | the payment | record key (partitioning) = `aggregateId` |

## 4. Consistency model

- **Strong (ACID) within each context:**
  - payment + saga + outbox
  - balance + reservation + outbox
  - settlement + outbox
  - journal (+ inbox)
- **Eventual across contexts.** The payment reaches SETTLED only after funds were captured. The ledger follows funds within about a second, and the reconciliation invariant is `ledger == available + reserved`.
- **Isolation trade-off of the saga.** Intermediate states are visible (RESERVED funds, PROCESSING payment). They are real business states, not dirty reads.

## 5. Where duplicates, reordering and loss can and cannot happen

| Can happen | Where | Why it is safe |
|---|---|---|
| Duplicate publication | Relay crash after the broker ack, before marking | Consumer inbox (same eventId) |
| Duplicate delivery | Consumer crash after commit, before the offset commit; rebalance | Inbox |
| Logical duplicates (new eventId) | Recovery re-issue, re-announcements | Natural keys + saga step guards |
| Reordering | Non-blocking retry topics | Step guards, reservation tombstone, capture rejects released funds |
| **Loss** | **Nowhere by design.** State and event commit together. Publication and consumption ack only after success. The DLT is durable and replayable. | Proven by the failure tests (docs/failures) |

## 6. Kafka unavailable

The API keeps accepting payments (201 CREATED). Events accumulate in the outboxes (`payflow.outbox.backlog`, `payflow.outbox.oldest.age.seconds` grow; `publish.failures` counts). Readiness stays UP. When the broker returns, the relays drain in order and the sagas complete. **Verified live:** backlog 2, age 5 s → 15 s, then SETTLED and backlog 0.

## 7. What WP-02 deliberately does not do

- Schema Registry (ADR-011 triggers).
- CQRS read models: no cross-context query need yet, and `payment.events` is the feed for them.
- Redis: no requirement that PostgreSQL cannot meet more safely.
- CDC (ADR-010).
- Resilience4j and load or chaos engineering (WP-03).
