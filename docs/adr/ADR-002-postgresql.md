# ADR-002: PostgreSQL for authoritative financial state

- Status: Accepted (WP-01)
- Date: 2026-09-26

## Context

Payments, idempotency records, settlements and ledger entries are money. The system needs:
- atomic multi-row writes (a payment and its idempotency record; a journal header and its lines)
- uniqueness under concurrency (idempotency keys, one settlement per payment, one journal per reference)
- declarative invariants that hold even when a bug or a manual fix bypasses the application
- exact decimal arithmetic
- mature operations: point-in-time recovery, replicas, and managed offerings such as RDS or Aurora

## Decision

Use PostgreSQL 18, with schemas `payment`, `account`, `ledger` and `settlement`.
- Migrations are owned by Flyway. Hibernate runs in `validate` mode only.
- Amounts are `NUMERIC(19,4)` plus `VARCHAR(3)` ISO-4217 currency with a CHECK on the format.
- Invariants are duplicated as CHECK constraints: positive amount, distinct parties, allowed statuses, and "failure reason present if and only if REJECTED/FAILED".
- The ledger is append-only, enforced by triggers and by privileges. A deferred constraint trigger enforces balanced journals at COMMIT.
- Unique constraints are the concurrency control for idempotency: `pk_idempotency_record`, `uq_settlement_payment` and `uq_journal_entry_reference`.
- Optimistic locking uses a `version` column on the mutable aggregates.
- Isolation is READ COMMITTED (see ADR-006 and the LLD).
- Primary keys are UUIDv7, which are time-ordered and give B-tree locality.

## Alternatives

1. **MongoDB for everything.** Multi-document transactions exist but need a replica set. There are no CHECK constraints or foreign keys, and schema validation is weaker than typed columns. Uniqueness works, but invariants live only in code. For money that is the wrong default.
2. **MySQL/InnoDB.** Viable, but gap-locking behaviour under REPEATABLE READ and weaker CHECK support (historically) make PostgreSQL the stronger fit for constraint-heavy schemas.
3. **A distributed SQL database (CockroachDB or Spanner).** It solves multi-region writes, which we do not need yet, at the cost of latency and operational complexity.

## Trade-offs

We gain:
- ACID transactions and declarative integrity.
- Well-understood locking semantics, proven by `IdempotencyConcurrencyIT`.

We accept:
- Vertical write scaling per database.
- Schema migrations as a deployment concern.
- Relational operational cost: vacuum, bloat and connection limits. PgBouncer and connection-pool sizing are WP-03 work.

## Consequences

- Every new invariant should get both a domain check and a database constraint where it is expressible.
- Database write throughput is the first scale ceiling (see the review, "10x").
- Reads can move to replicas later. Idempotency and version checks must always hit the primary.
