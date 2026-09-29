# PayFlow: High-Level Design (as of WP-03)

## 1. Purpose and scope

PayFlow accepts payment instructions between accounts, screens them for fraud, settles them over external rails (card, UPI, bank transfer), and records the financial effect in a double-entry ledger.

WP-01 delivers the backend foundation:
- the domain model
- persistence
- the API
- security
- idempotency
- concurrency control
- architecture governance

This document grew with the work packages: asynchronous processing was added in WP-02 (§5, §7), and resilience, scale and production engineering in WP-03 (§6, §7b). Detailed designs: WP-01-LLD, WP-02-LLD, WP-03-LLD.

## 2. Context

```
 Customer app ──(OIDC Auth Code+PKCE)──▶ Identity Provider (Keycloak)
      │  JWT(aud=payflow-api, scopes)             ▲
      ▼                                           │ client credentials
 ┌──────────────────────── PayFlow (one deployable) ───────────────────────┐
 │  REST /api/v1  ── Spring Security (JWT verify + scope rules, deny-all) │
 │                                                                         │
 │   Payment ──ACL──▶ Account      Payment ──ACL──▶ Fraud ──▶ MongoDB      │
 │      │  └──ACL──▶ Settlement ──▶ external rails (simulated in WP-01)    │
 │      └────ACL──▶ Ledger                                                 │
 │   PostgreSQL schemas: payment | account | ledger | settlement           │
 └─────────────────────────────────────────────────────────────────────────┘
        ▲ Payment orchestrator / ops workloads (JWT via client credentials)
```

## 3. Bounded contexts

| Context | Responsibility | Store | Key invariants |
|---|---|---|---|
| **Payment** | Payment lifecycle and orchestration; idempotent creation | PG `payment` | state machine; payer ≠ payee; amount > 0; one payment per (client, key) |
| **Account** | Ownership, currency and status of accounts | PG `account` | owner required; frozen accounts cannot transact |
| **Fraud** | Risk scoring and the evidence behind it | MongoDB `payflow_fraud` | one assessment per payment; decision never flips on retry |
| **Ledger** | Append-only double-entry journal; derived balances | PG `ledger` | debits = credits; single currency; immutable; one journal per reference |
| **Settlement** | Instructions to external rails, per-rail strategies | PG `settlement` | one settlement per payment; provider idempotency key = payment id |

Deployment: a modular monolith (ADR-001). Boundaries are enforced by ArchUnit, so extraction means swapping an adapter.

## 4. Payment lifecycle

```
CREATED ──authorize──▶ AUTHORIZED ──process──▶ PROCESSING ──▶ SETTLED
   │ └─(risk/eligibility)─▶ REJECTED              └──(rail decline)─▶ FAILED
   └─cancel─▶ CANCELLED ◀─cancel─ AUTHORIZED
```

Semantics:
- **REJECTED** is a business decision. Do not retry the same payment.
- **FAILED** is a rail outcome after submission.
- **CANCELLED** happens before funds are in flight.

The proposed `VALIDATING` state was dropped: validation is synchronous inside the create transaction, so a persisted "validating" state could never be observed (see LLD §3).

## 5. Key flows (WP-02, asynchronous saga)

1. **Create.** `POST /payments` with `Idempotency-Key`.
   - Ownership, eligibility and currency are checked via Account.
   - **One transaction** inserts the payment, the idempotency record and the saga (AWAITING_RISK), and writes `PaymentCreated` and `AssessPaymentRisk` to the Payment **outbox**.
   - The response is 201 with status CREATED.
2. **Saga** (orchestrated by Payment over Kafka; see SAGA-DESIGN):
   `AssessPaymentRisk` → Fraud (MongoDB) → `RiskAssessed` → `ReserveFunds` → Account (row lock, CHECK) → `FundsReserved` → payment AUTHORIZED/PROCESSING → `SubmitSettlement` → Settlement (rail) → `SettlementCompleted` → `CaptureFunds` → `FundsCaptured` → payment SETTLED.
   Declines and timeouts compensate with `ReleaseFunds`.
3. **Ledger** follows `funds.events` (captures, deposits) as a choreographed, idempotent follower.
4. **Recovery.** A scanner re-issues the commands of overdue steps and compensates only where the outcome is known; otherwise it escalates to MANUAL_REVIEW.

WP-01's synchronous `authorize`/`process` endpoints were removed (see WP-02-LLD §1).

## 6. Quality attributes and how they are met

| Attribute | Mechanism |
|---|---|
| Correctness of money | `Money` with BigDecimal at currency scale and reject-not-round; NUMERIC(19,4); balanced journals enforced in both code and the database |
| No duplicate payments | Idempotency-Key with a PK-arbitrated insert, atomic with the payment |
| No lost updates | Optimistic version checks on the aggregates; unique constraints for one-of invariants |
| Security | JWT verification, per-route scopes, deny-by-default, ownership checks returning 404, least-privilege DB and container |
| Availability | MongoDB outage degrades only authorization (fail closed); readiness excludes MongoDB; fail-fast timeouts |
| Evolvability | Clean Architecture per context; ports for events, resilience and remote calls |
| Observability | ECS JSON logs with traceId, spanId and correlationId; health, liveness and readiness probes; W3C trace context; WP-03: Prometheus (authenticated scrape), Grafana dashboards, Tempo traces, SLO burn-rate alerts (ADR-020) |
| Performance and overload (WP-03) | pipelined outbox relay; bounded intake (in-flight bulkhead, admission control on outbox age and consumer lag); G1; measured capacity and SLOs (docs/performance, docs/sre) |
| Resilience (WP-03) | settlement rails over HTTP with timeout, bounded retry with jitter, per-rail circuit breaker, bulkheads; NOT_SENT vs UNKNOWN outcomes (ADR-017) |
| Financial operations (WP-03) | manual review with rail evidence, no forced outcomes (ADR-022); continuous reconciliation (ADR-021) |
| Governance | 20 ArchUnit rules plus negative tests proving the rules detect violations |

## 7. Event backbone (WP-02)

- Kafka 4.2 (KRaft), 7 topics grouped per context: `payment.events`, `{fraud,funds,settlement}.{commands,events}` (KAFKA-TOPIC-CATALOG).
- Transactional Outbox in payment, account and settlement; polling relay (ADR-009/010).
- Inbox in payment, account and ledger; natural keys in settlement and fraud (ADR-013).
- Retry topics and a sanitized DLT per consumer group, plus a replay API (ADR-014).
- JSON contracts plus schemas as code (ADR-011/016).

## 7b. Production engineering (WP-03)

```
             ┌────────────── edge: JWT → per-subject rate limit → admission (outbox age, consumer lag) → in-flight bulkhead
client ──────┤
             └─▶ PayFlow (2 vCPU / 1.5 GiB, G1) ──▶ PostgreSQL (authoritative; outbox, inbox, saga, ledger, reconciliation)
                    │  pipelined relay (5,500 ev/s)          │
                    ▼                                        │
                 Kafka (SASL/SCRAM, ACLs) ──▶ consumers (retry topics, DLT) ──▶ saga (recovery holds on backlog)
                    │
                    └─ settlement adapter ──HTTP──▶ rail (timeout, retry+jitter, circuit breaker, bulkhead) [simulator in lab]
Prometheus ◀── /actuator/prometheus (token)   Tempo ◀── OTLP traces   Grafana: 9 dashboards   23 alerts
```

Measured (docs/performance/TUNING-RESULTS.md):
- Completion capacity of one instance ≈ 45–50 payments/s (consumer-bound). Acceptance alone ≈ 375/s (CPU-bound).
- Beyond capacity, load is shed with 503 + Retry-After instead of queueing.

Operations:
- Manual-review API (`/api/v1/ops/manual-reviews`).
- Reconciliation (`/api/v1/ops/reconciliation`).
- Runbooks and alerts (docs/sre).
- Kubernetes manifests (`deploy/k8s`: restricted pods, probes, PDB, HPA, NetworkPolicy).

## 8. Roadmap hooks

| Extension point | Used by |
|---|---|
| `PaymentEventPublisherPort` (called in-transaction) | Implemented in WP-02 as the outbox adapter |
| `SettlementGatewayPort` / relay | Delivered in WP-03 (HTTP rail adapter with Resilience4j; pipelined relay). Next: CDC or sharded relay at 10× (ADR-019) |
| Per-service Kafka principals (ACL matrix applied) | Service extraction: each context runs with its own identity (ADR-023) |
| `reconciliation` read model | Incremental reconciliation and finance-approved correction journals |
| `deploy/k8s` base | Platform WP: operators for PostgreSQL/Kafka, External Secrets, service mesh (mTLS), KEDA |
