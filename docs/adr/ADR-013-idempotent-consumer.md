# ADR-013: Idempotent consumers: inbox where the effect is one local transaction, natural keys elsewhere

- Status: Accepted (WP-02)
- Date: 2026-09-27

## Context

Delivery is at-least-once. The outbox can re-send, and Kafka redelivers when a consumer crashes after its database commit but before its offset commit. Replays and recovery re-issue commands with **new** event ids.

## Decision

Offsets:
- `AckMode.RECORD` with `enable.auto.commit=false`. The container commits a record's offset only after the listener returns, and the listener returns only after the business transaction committed.
- Kafka's exactly-once (transactions) is not used end to end (see "Exactly-once" below).

Deduplication: three layers.

| Layer | Mechanism | Covers |
|---|---|---|
| 1. Inbox | `<schema>.processed_event (consumer, event_id)` primary key, claimed inside the same transaction as the business change (`IdempotentExecutor`). Concurrent duplicates block on the key and the second becomes a no-op. | Redelivery of the **same** eventId (crash before ack, outbox re-send, DLQ replay) |
| 2. Natural business keys | one reservation per payment, one settlement per payment, one journal per reference, one fraud assessment per payment (MongoDB unique index) | The same **fact** re-sent with a new eventId (recovery re-issue, re-announcement) |
| 3. State-machine guards | saga replies that do not match the current step are STALE; `release` on RELEASED/REJECTED is a no-op; `capture` on CAPTURED re-announces | Logically duplicate or late messages |

Where each mechanism is used:
- **Inbox:** Payment saga, Account funds, Ledger. Their effect is a single PostgreSQL transaction.
- **Natural keys only:** Settlement (its handling spans a rail call, so it cannot be one transaction; resumable PENDING plus provider idempotency key = paymentId) and Fraud (MongoDB; consume-process-produce: persist the assessment, then publish synchronously, then commit the offset, so a crash re-derives and re-publishes the stored decision).

## Exactly-once: what we do *not* claim

Kafka's EOS (idempotent producer plus transactions, `read_committed`) gives exactly-once **within Kafka** (consume → produce to Kafka). Our side effects live in PostgreSQL, MongoDB and an external rail, and none of them take part in Kafka transactions. PayFlow therefore provides:

> **at-least-once delivery + idempotent processing + business-key deduplication + reconciliation = effectively-once business effects**

## Alternatives

| Option | Why not chosen |
|---|---|
| Auto-commit offsets | Commits on a timer, possibly before processing, so a crash loses the message. |
| Commit before processing | At-most-once, so messages are lost. |
| Redis SETNX dedup | A second store, a dual write, and dedup lost on eviction or failover. PostgreSQL in the same transaction is strictly stronger. |
| Kafka transactions end to end | Does not cover database or rail side effects. |

## Trade-offs

- One extra INSERT per consumed event.
- An inbox retention window (7 days). Duplicates older than that fall through to the natural keys (layer 2).

## Failure implications

Each case below is tested (`ConsumerSemanticsIT`):

| Case | Outcome |
|---|---|
| Crash before commit | Rollback, then retried and applied once |
| Crash after commit, before ack | Redelivered, then DUPLICATE (no effect) |
| Same event twice | One effect |
| Replay twice | One effect |
| Concurrent duplicate reservation | Held once (`FundsConcurrencyIT`) |

## Operational consequences

- Metric `payflow.events.consumed{outcome=DUPLICATE|STALE}` shows the redelivery rate. A spike indicates rebalances or relay crashes.
