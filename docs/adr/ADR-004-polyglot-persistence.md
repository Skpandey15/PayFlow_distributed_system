# ADR-004: Polyglot persistence with database-per-context and no distributed transactions

- Status: Accepted (WP-01)
- Date: 2026-09-26

## Context

PostgreSQL (ADR-002) and MongoDB (ADR-003) coexist. Each bounded context must own its data so that it can evolve and be extracted independently (ADR-001). Some business flows touch several stores. For example, "authorize" writes a fraud assessment (MongoDB) and a payment status (PostgreSQL). "Settle" writes a settlement row, a payment status and a journal entry.

## Decision

1. **Ownership.**
   - Each context owns its schema or collection.
   - Nothing else reads or writes it: no cross-schema foreign keys, joins or views.
   - Access to another context's data goes only through that context's inbound port (ArchUnit enforced).
2. **No shared database as an integration mechanism.** One PostgreSQL *instance* hosts four schemas for local economy, but that is an operational convenience, not an integration contract.
3. **No XA/2PC** across PostgreSQL and MongoDB, or across contexts. Each step is a local transaction and is:
   - *idempotent*, using natural keys (payment id or journal reference) with unique constraints
   - *ordered* so that a crash leaves a resumable state
   - *re-drivable*, because `process` and `authorize` can be called again
4. **Cross-context consistency is eventual.** WP-01 re-drives on request. WP-02 makes it automatic with a Transactional Outbox plus Kafka plus Sagas.

## Why not XA/2PC?

- MongoDB does not take part in XA transactions.
- 2PC holds locks across network round-trips, so throughput collapses under contention. It also blocks when the coordinator fails ("in-doubt" transactions), which is a new availability risk.
- It couples the availability of every participant: any database down means no payments.
- It does not survive the extraction into services with independent stores anyway.

## Trade-offs

We gain:
- Autonomy.
- Each store chosen for its workload.
- Failure isolation (the MongoDB outage demonstration).

We accept:
- Windows of inconsistency that must be designed for: the dual write, documented in the LLD and the review (finding M1).
- Reconciliation responsibilities.
- More complex reasoning about ordering.

## Consequences

- Every cross-context write sequence must be written as "record intent, then call, then record outcome", with idempotency keys.
- WP-02 must deliver a Transactional Outbox before any context is extracted.
- Reporting that needs data from several contexts must be built from events (CQRS read models, WP-02). It must never be built from cross-schema SQL.
