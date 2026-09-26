# ADR-003: MongoDB for fraud assessments (evidence), and only there

- Status: Accepted (WP-01)
- Date: 2026-09-26

## Context

A fraud assessment is a self-contained, write-once record that the payment core never joins against. It contains:
- a score and a decision
- a variable list of signals, which grows as rules are added
- device and network context, where fields vary by channel
- a model version
- (in the future) analyst investigation notes

Its schema evolves at the pace of the fraud team, not the payments team. It is read by payment id and by payer over a time window (velocity).

## Decision

Store `FraudAssessment` documents in the `payflow_fraud.fraud_assessments` collection:

| Aspect | Decision |
|---|---|
| Uniqueness | Unique index `ux_payment_id` gives one assessment per payment, which makes assessment idempotent |
| Velocity query | Index `ix_payer_assessed_at` |
| Evolution | A `schemaVersion` field. Additive changes only; readers tolerate missing fields |
| Money | `Decimal128` for the amount, never a double |
| Durability | `writeConcern: majority` and `readConcern: majority`, so decisions survive failover |
| Timeouts | 2s server-selection and connect, 3s socket. An outage becomes a fast 503, not a 30-second thread stall |
| Access | App user `payflow_fraud_app` with `readWrite` on `payflow_fraud` only |

## Why not PostgreSQL?

PostgreSQL could do this with a JSONB column, and that is a legitimate alternative. We chose MongoDB because:
1. The workload is document-shaped end to end (write whole, read whole) with no relational integrity needs.
2. Schema evolution belongs to a different team.
3. The context is an extraction candidate with a different scaling profile.

It also makes the polyglot trade-offs (ADR-004) concrete and testable in this lab. With JSONB we would lose none of the guarantees we need. **The decision is defensible, not inevitable.**

## Consistency requirements

- There is no transaction spanning MongoDB and PostgreSQL.
- The assessment is written first (in MongoDB). Then the payment decision is committed in PostgreSQL.
- If the process dies between the two writes, the payment is still CREATED. A retry finds the existing assessment (unique index) and applies the same decision. The two stores converge, and a decision never flips.

## Failure mode: MongoDB unavailable

- Authorization fails closed with `503 FRAUD_CHECK_UNAVAILABLE` and `Retry-After`. The payment stays CREATED.
- Payment creation, reads, cancellation, settlement of already-authorized payments, and the ledger all keep working.
- The readiness probe excludes MongoDB on purpose, so pods are not pulled from the load balancer.
- This was verified live: after `docker stop` of MongoDB, authorize returned 503 in about 2.3s, the payment stayed CREATED, and a retry after recovery authorized it.

## Explicit non-decision

Balances, ledger entries, payments and settlements **never** live in MongoDB (ADR-002).

## Trade-offs and consequences

- Two database technologies to operate, back up and secure.
- The local compose setup runs a standalone mongod. Production needs a replica set for the majority concerns to mean anything (review finding O4).
- Index creation happens at startup and is best-effort (review finding m2).
