# WP-01 Low-Level Design

> **Superseded in part by WP-02.** The synchronous `authorize` and `process` use cases and endpoints (§3, §6, §7) were
> replaced by the asynchronous orchestrated saga, with funds reservation and Transactional Outbox. See
> [WP-02-LLD](WP-02-LLD.md) and [SAGA-DESIGN](SAGA-DESIGN.md). Everything else in this document still applies.

## 1. Code layout

```
com.payflow
├── shared/{domain,application}          shared kernel: Money, AccountId, Identifiers(UUIDv7), Actor,
│                                         TransactionRunner, exception categories, PageQuery/PageResult
├── platform/{web,security,persistence,observability,config}
│                                         cross-cutting: problem details, JWT resource server,
│                                         TransactionRunner impl, correlation id, clock, scheduling
└── <context> ∈ {payment, account, fraud, ledger, settlement}
    ├── domain/                           aggregates, value objects, domain services, events
    ├── application/port/in               use-case interfaces + commands/views (published API)
    ├── application/port/out              repository / gateway / ACL ports
    ├── application/usecase               use-case implementations (plain Java)
    ├── adapter/in/web                    REST controllers + DTOs
    ├── adapter/out/persistence           JPA entities or Mongo documents, mappers, repository adapters
    ├── adapter/out/<other-context>       anti-corruption layers (payment only)
    └── infrastructure/                   composition root (@Configuration), jobs, properties
```

## 2. Domain model

### 2.1 Money (shared kernel)

- `record Money(BigDecimal amount, Currency currency)` is normalised to the currency's minor units (USD=2, JPY=0, KWD=3).
- **Excess precision is rejected, never rounded** (`MONEY_PRECISION_EXCEEDED`).
- There are at most 15 integer digits, which fits `NUMERIC(19,4)`.
- There is no cross-currency arithmetic (`MONEY_CURRENCY_MISMATCH`).
- Currencies without a minor unit, such as XAU, are rejected.
- Rounding policy for derived amounts (fees and FX, both future work): HALF_EVEN at the currency scale. No derived amounts exist in WP-01.

### 2.2 Payment aggregate

| Item | Detail |
|---|---|
| Identity and parties | `PaymentId` (UUIDv7), payer/payee `AccountId`, `Money`, `PaymentMethod`, reference (≤140), `initiatedBy` (JWT `sub`) |
| Lifecycle | `status`, `failureReason`, timestamps, and `version` (the concurrency token, never changed by the domain) |
| Behaviour | `initiate`, `authorize`, `reject(reason)`, `cancel`, `startProcessing`, `markSettled`, `markFailed(reason)`, each recording a sealed `PaymentEvent` |
| State machine | table-driven, in `PaymentStatus.canTransitionTo`. Invalid transitions throw `InvalidStateTransitionException` (409) |
| Persistence boundary | `PaymentSnapshot` rehydrates and externalises state without setters or JPA annotations |

### 2.3 Why the proposed `VALIDATING` state was dropped

Validation (ownership, eligibility, currency) runs synchronously before the create transaction commits. A `VALIDATING` row would therefore never be visible to anyone. It would only be meaningful with asynchronous validation, which is WP-02, and even then `CREATED` plays that role. We also split the original single failure state into:
- `REJECTED`: a business "no", not retryable for the same payment
- `FAILED`: the rail declined after submission

The split matters for customer messaging, retries and reporting.

### 2.4 Other aggregates

| Aggregate | Design notes |
|---|---|
| **Account** | Status ACTIVE or FROZEN, ownership check, `canTransact`. Deliberately thin: no balance, because balance is derived from the ledger |
| **JournalEntry** | At least 2 lines, a single currency, sum(debit) = sum(credit), reference required. Immutable. `LedgerLine.signedAmount()` follows the liability convention (credit +, debit −) |
| **Settlement** | PENDING → COMPLETED(providerRef) or DECLINED(reason). One per payment |
| **FraudAssessment** | Immutable record. `RiskScoringPolicy` (domain service) sums the `RiskRule` strategies: HighAmount, Velocity, MissingDevice, HighRiskCountry. Score capped at 100; decline at ≥ 70; `modelVersion` stamped on each assessment |

## 3. Transaction boundaries

All boundaries are expressed with `TransactionRunner`, implemented by `SpringTransactionRunner`:
- READ COMMITTED isolation
- REQUIRED propagation
- a 5-second timeout
- a read-only variant for queries

| Use case | Transaction(s) | Outside any transaction |
|---|---|---|
| CreatePayment | T1: INSERT payment, INSERT idempotency_record, publish events | idempotency fast-path read (read-only); Account lookups |
| Authorize | T1 (read-only): load. T2: reload, transition, versioned UPDATE, publish | Account eligibility; **Fraud assessment (MongoDB)** |
| Process | T1: AUTHORIZED→PROCESSING (claim). T2: PROCESSING→SETTLED or FAILED | **Settlement submit** (own local T1/T2 in the settlement schema); **ledger posting** (own local transaction) |
| Cancel | T1: load, visibility check, transition, versioned UPDATE | nothing |
| Settlement submit | T1: find-or-create PENDING. T2: reload, complete or decline | **gateway call** |
| Ledger post | T1: find-by-reference, else INSERT header and lines (balance trigger at COMMIT) | nothing |

Principles:
1. **No network call and no cross-context call inside a transaction.** Long transactions hold pooled connections and row locks, which lets a slow dependency take down the database tier.
2. **Reload inside the write transaction.** The decision (for example the fraud verdict) was computed on a snapshot. The versioned UPDATE detects anything that changed since.
3. **Rollback behaviour.** Any exception inside `inTransaction` rolls back everything in that transaction: the payment and the idempotency row, or a journal header and its lines. Steps already committed in earlier transactions are resumable by design.
4. **Why READ COMMITTED is enough.** Every race we care about is arbitrated by a unique index or a version predicate, and both behave correctly under READ COMMITTED. SERIALIZABLE would add abort-and-retry churn for no extra guarantee.

## 4. Concurrency control

| Race | Protection | Evidence |
|---|---|---|
| Duplicate create (same key) | PK `(client_id, idempotency_key)`. Loser re-reads the winner | `IdempotencyConcurrencyIT` (16 threads × 3) |
| Lost update (cancel vs authorize) | `version` check plus `UPDATE … WHERE version=?` gives `ConcurrencyConflictException` (409) | `PaymentPersistenceIT.staleWrite…` |
| Double settlement submission | Claim by versioned UPDATE; `uq_settlement_payment`; provider idempotency key; `uq_journal_entry_reference` | `ProcessConcurrencyIT` (8 threads × 3: 1 settlement, 1 journal, correct balances) |
| Duplicate fraud assessment | Mongo unique index `ux_payment_id` | `FraudAssessmentMongoIT` |

**Why application validation alone is insufficient.** "Check, then act" is two steps. Between them another transaction can commit, whether that is another request, another replica or a retry. Only the database can make the check and the write atomic, through a unique index or a conditional UPDATE. The application check stays as a fast path and to produce good error messages.

**Optimistic vs pessimistic locking.** Conflicts on a single payment are rare (one customer, one orchestrator), so optimistic locking avoids holding locks and keeps transactions short. Pessimistic `SELECT … FOR UPDATE` would fit hot rows such as a balance row with many concurrent postings. The ledger avoids that hot row entirely by using append-only lines and derived balances.

## 5. Idempotency

See ADR-006. Flow: fast-path read → validate → one transaction {payment + record} → on 23505 `pk_idempotency_record` → replay the winner.

## 6. API

| Method | Path | Scope | Notes |
|---|---|---|---|
| POST | /api/v1/payments | payments:write | `Idempotency-Key` required. 201 + Location + `Idempotent-Replayed` |
| GET | /api/v1/payments/{id} | payments:read | 404 if not visible |
| GET | /api/v1/payments?status&page&size | payments:read | own payments only (admin: all); size ≤ 100 |
| POST | /api/v1/payments/{id}/cancel | payments:write | idempotent |
| POST | /api/v1/payments/{id}/authorize | payments:process | optional checkout-channel body |
| POST | /api/v1/payments/{id}/process | payments:process | resumable. 503 leaves the payment PROCESSING |
| POST | /api/v1/accounts | accounts:write | owner = caller |
| GET | /api/v1/accounts/{id} | accounts:read | owner or accounts:admin |
| POST | /api/v1/accounts/{id}/freeze | accounts:admin | |
| GET | /api/v1/ledger/accounts/{id}/balance?currency | ledger:read | |
| GET | /api/v1/fraud/assessments/{paymentId} | fraud:read | |

- Amounts are **decimal strings** ("125.50"), never JSON numbers.
- Errors use RFC 9457 `application/problem+json` with `type`, `title`, `status`, `detail`, `code` and `correlationId` (plus `errors[]` on 400).
- Status mapping:

  | Status | Meaning |
  |---|---|
  | 400 | syntax |
  | 401 | no or invalid token |
  | 403 | scope |
  | 404 | not found or not visible |
  | 409 | state conflict or concurrency |
  | 422 | business rule |
  | 503 | dependency unavailable (with `Retry-After`) |

- OpenAPI is served at `/v3/api-docs`, with Swagger UI at `/swagger-ui.html`.

## 7. Failure behaviour

| Failure | Behaviour |
|---|---|
| MongoDB down | Authorize fails fast (2s) with 503. Payment stays CREATED. Everything else works; readiness stays UP. *Verified live.* |
| Rail timeout or unavailable | 503. Payment stays PROCESSING and settlement stays PENDING. Re-invoking `process` resumes with the same idempotency key |
| Crash after the SETTLED commit, before the ledger post | Payment is SETTLED without a journal until `process` is re-invoked. **This is the dual write, fixed in WP-02** by the Outbox |
| PostgreSQL down | Readiness DOWN (pod removed from the load balancer). Liveness UP (no restart storm). Requests fail fast (3s pool timeout) |
| Concurrent modification | 409 `CONCURRENT_MODIFICATION`; nothing written; safe to retry |

## 8. Scalability notes

- The application is stateless and scales horizontally. Idempotency, locking and uniqueness live in PostgreSQL, so N replicas are safe.
- Request handling runs on virtual threads. **The Hikari pool (20) is the effective bulkhead** for database work, and its 3s acquisition timeout fails fast under saturation.
- Hot paths are all index-backed. The ledger balance is O(lines per account), and snapshots are planned (review finding m1).

## 9. Operational notes

- Flyway runs as the owner role `payflow_migrator`. The app connects as `payflow_app` with DML only.
- `R__runtime_role_grants.sql` must be edited, so that its checksum changes, whenever a migration adds a table. Otherwise the runtime role will not get privileges on it (review finding m7).
- Structured ECS JSON logs are on by default, and plain-text logs are used in the `local` and `test` profiles.
