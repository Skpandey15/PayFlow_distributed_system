# Data Architecture

## Ownership map

| Context | Store | Objects | Owner role |
|---|---|---|---|
| Account | PostgreSQL | `account.account`, `account.account_balance`, `account.funds_reservation`, `account.funds_deposit`, `account.outbox_event`, `account.processed_event` | payflow_migrator (DDL) / payflow_app (DML) |
| Payment | PostgreSQL | `payment.payment`, `payment.idempotency_record`, `payment.payment_saga`, `payment.outbox_event`, `payment.processed_event` | same |
| Ledger | PostgreSQL | `ledger.journal_entry`, `ledger.ledger_entry` (append-only), `ledger.processed_event` | same; runtime role has SELECT and INSERT only (+ DELETE on the inbox for purge) |
| Settlement | PostgreSQL | `settlement.settlement`, `settlement.outbox_event` | same |
| Fraud | MongoDB | `payflow_fraud.fraud_assessments` | payflow_fraud_app (readWrite, one database) |

Rules:
- There are no cross-schema foreign keys, joins or views.
- `payment.payer_account_id` is a reference by identity. Its validity is checked through `LookupAccountUseCase`, not through a foreign key.

## Monetary representation

| Aspect | Rule |
|---|---|
| Java | `BigDecimal` inside `Money`. `double` and `float` are banned (ArchUnit) |
| Scale | the currency's minor units (ISO 4217): USD 2, JPY 0, KWD 3. Normalised on construction |
| Input | a decimal **string** in the API. Excess precision is **rejected** (422), never rounded |
| PostgreSQL | `NUMERIC(19,4)`: 15 integer digits and 4 fraction digits, enough for any ISO minor unit. Currency is `VARCHAR(3)` with a CHECK on `^[A-Z]{3}$` |
| MongoDB | `Decimal128` plus a currency string |
| Rounding (future derived amounts) | HALF_EVEN at the currency scale |
| Cross-currency | forbidden; no implicit FX |

## Constraints (defence in depth)

| Table | Constraint | Purpose |
|---|---|---|
| payment | `ck_payment_amount_positive`, `ck_payment_distinct_parties`, `ck_payment_status`, `ck_payment_method`, `ck_payment_currency_iso`, `ck_payment_failure_reason` | mirror domain invariants |
| idempotency_record | `pk_idempotency_record (client_id, idempotency_key)`, FK to payment, `ck_idempotency_expiry` | idempotency arbitration |
| journal_entry | `uq_journal_entry_reference`; UPDATE/DELETE/TRUNCATE-rejecting triggers | idempotent, immutable postings |
| ledger_entry | `ck_ledger_entry_amount`, `ck_ledger_entry_direction`, `uq_ledger_entry_line`; **deferred constraint trigger** `trg_ledger_entry_balanced` | balanced, single-currency journals checked at COMMIT |
| settlement | `uq_settlement_payment`, `ck_settlement_completed_has_ref` | one instruction per payment |
| account | `ck_account_status`, `ck_account_currency_iso` | |

All of these were proven with direct SQL that bypasses the domain (`PaymentPersistenceIT`, `LedgerPersistenceIT`).

## Indexes (each one serves a named query)

| Index | Query |
|---|---|
| `ix_payment_initiator_created (initiated_by, created_at DESC, id)` | "my payments, newest first", with stable pagination |
| `ix_payment_status_created (status, created_at DESC)` | operations filtering by status |
| `ix_idempotency_expires_at` | retention purge |
| `ix_ledger_entry_account_currency` | balance derivation |
| `ix_settlement_status_updated` | reconciliation of stuck PENDING rows (WP-03 sweeper) |
| `ix_account_owner_subject` | ownership lookups |
| Mongo `ux_payment_id` (unique), `ix_payer_assessed_at` | idempotent assessment; velocity |

## Optimistic locking

A `version BIGINT` column on payment, account and settlement, handled with JPA `@Version`. The adapter additionally compares the stored version with the aggregate's version before copying state. That catches changes made between an earlier read transaction and the write transaction, which Hibernate's own check alone would miss.

## Audit timestamps

`created_at` and `updated_at` are `TIMESTAMPTZ`. They are set by the domain from an injected UTC clock truncated to microseconds, which is PostgreSQL's precision, so round-trips are exact. The ledger stores `posted_at` and is immutable.

## Candidate tables not created in WP-01 (and why)

| Table | Reason |
|---|---|
| `payment_attempt` | Settlement already records the attempt, and retries reuse it through idempotency keys. Multiple attempts per payment (for example cascading rails) are WP-03 |
| `refund` | A new lifecycle (partial refunds, reversal journals). Out of WP-01 scope; the ledger's compensating-entry model is ready for it |
| `outbox` | Delivered in WP-02 (V6) |

## Consistency model

| Scope | Model |
|---|---|
| Inside a context | strong (ACID), with READ COMMITTED plus constraints and version checks |
| Across contexts or stores | eventual, with idempotent steps that can be re-driven. There is no XA/2PC (ADR-004). WP-02 automates convergence with the Outbox and Sagas |

## WP-02 additions

| Table | Key constraints | Purpose |
|---|---|---|
| `account.account_balance` | PK account_id; `CHECK available >= 0`, `CHECK reserved >= 0` | spendable vs held funds; row lock per mutation |
| `account.funds_reservation` | UNIQUE payment_id; status CHECK | one hold per payment; tombstone support |
| `account.funds_deposit` | PK = client depositId | idempotent deposits |
| `<ctx>.outbox_event` | BIGSERIAL id (publication order); UNIQUE event_id; partial index on unpublished rows | Transactional Outbox |
| `<ctx>.processed_event` | PK (consumer, event_id) | inbox / idempotent consumer |
| `payment.payment_saga` | UNIQUE payment_id; step CHECK; partial index on in-flight steps | orchestration state and recovery scan |

The reconciliation invariant for customer accounts, once the ledger has caught up, is `ledger balance = available + reserved`. It is asserted in `PaymentApiIT` and was observed live.

## Retention

| Data | Retention |
|---|---|
| Idempotency records | at least 24h, purged hourly |
| Outbox rows (published) | 7 days, then purged |
| Inbox rows | 7 days (dedup window; natural keys beyond it) |
| Kafka topics / DLTs | 7 days |
| Ledger | forever (append-only) |
| Fraud assessments | policy to be defined with compliance. A TTL index would be the mechanism |
