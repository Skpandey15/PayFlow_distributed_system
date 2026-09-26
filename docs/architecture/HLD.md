# PayFlow: High-Level Design (as of WP-01)

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

Asynchronous processing (WP-02) and resilience and scale (WP-03) are out of scope. This design leaves explicit extension points for them.

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

## 5. Key flows (WP-01, synchronous)

1. **Create.** `POST /payments` with `Idempotency-Key`.
   - Ownership, eligibility and currency are checked via Account.
   - One transaction inserts the payment and the idempotency record, and publishes `PaymentCreated`.
2. **Authorize.** The processor identity calls it.
   - Eligibility is checked via Account, then risk via Fraud (MongoDB), both outside any transaction.
   - A short transaction then records AUTHORIZED or REJECTED.
3. **Process.** The processor identity calls it.
   - Transaction 1 claims the payment (AUTHORIZED → PROCESSING).
   - Settlement runs, outside any transaction and idempotent.
   - Transaction 2 records SETTLED or FAILED.
   - The idempotent ledger posting follows.

   Every step is resumable.

## 6. Quality attributes and how they are met

| Attribute | Mechanism |
|---|---|
| Correctness of money | `Money` with BigDecimal at currency scale and reject-not-round; NUMERIC(19,4); balanced journals enforced in both code and the database |
| No duplicate payments | Idempotency-Key with a PK-arbitrated insert, atomic with the payment |
| No lost updates | Optimistic version checks on the aggregates; unique constraints for one-of invariants |
| Security | JWT verification, per-route scopes, deny-by-default, ownership checks returning 404, least-privilege DB and container |
| Availability | MongoDB outage degrades only authorization (fail closed); readiness excludes MongoDB; fail-fast timeouts |
| Evolvability | Clean Architecture per context; ports for events, resilience and remote calls |
| Observability | ECS JSON logs with traceId, spanId and correlationId; health, liveness and readiness probes; W3C trace context |
| Governance | 20 ArchUnit rules plus negative tests proving the rules detect violations |

## 7. Roadmap hooks

| Extension point | Used by |
|---|---|
| `PaymentEventPublisherPort` (called in-transaction) | WP-02 Outbox and Kafka |
| `LedgerPostingPort`, `FraudAssessmentPort`, `SettlementProviderPort` | WP-02 async and Saga; WP-03 Resilience4j decorators |
| `SettlementGatewayPort` (per rail) | WP-03 circuit breaker, bulkhead, timeout and retry per rail |
| Readiness groups, tracing export flag | WP-03 SLOs and collectors |
