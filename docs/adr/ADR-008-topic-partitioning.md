# ADR-008: Topic granularity and paymentId partitioning

- Status: Accepted (WP-02)
- Date: 2026-09-27

## Context

A payment's messages must be processed in causal order: reserve before capture, and reserve before a compensating release. Kafka guarantees ordering **only within a partition**, and the partition is chosen by the record key. Topic granularity also determines ACL granularity (ADR-005), schema ownership and consumer fan-out.

## Decision

- **Seven topics, grouped per context.**
  - Command topics are owned by the receiver: `fraud.commands`, `funds.commands`, `settlement.commands`. Only the Payment saga writes them.
  - Event topics are owned by the producer: `payment.events`, `fraud.events`, `funds.events`, `settlement.events`.
  - Retry and DLT topics are per consumer group: `<topic>-<group>-retry-N` and `<topic>-<group>-dlt`.
- **Key = paymentId** for every workflow message. Account-level facts (`FundsDeposited`) use key = accountId.
- Every consumer enforces `key == aggregateId`, because a mismatch would silently break ordering.
- **Partitions:** 6 per topic (local and test: 3). This is a deliberate over-provision against today's load, because increasing partitions later remaps keys and breaks ordering during the change.

## Alternatives

| Option | Why not chosen |
|---|---|
| One topic per event type (`payment-created`, …) | Ordering across types of one payment is lost, because they would sit on different topics. Too many ACLs and topics. |
| One topic for the whole workflow (`payment-workflow`) | Perfect ordering, but every service reads every message (least-privilege and PII problem: fraud evidence would reach Ledger), and there is one blast radius. |
| **Per context, commands vs events (chosen)** | ACLs follow ownership. Consumers read only what they need. Per-payment ordering holds inside each topic, and cross-topic ordering is provided by the saga (one outstanding command per payment). |
| Key = accountId | Would order all of an account's payments. That is not needed, and it creates hot partitions for merchants. |

## Ordering guarantees (precisely)

- **Within a topic partition:** records for one paymentId are consumed in the order they were appended.
- **Append order equals commit order per payment.** The outbox relay is a single writer per outbox, publishes in id order and stops at the first failure. The idempotent producer keeps partition order across its own retries.
- **Across topics:** no guarantee. It is not needed, because the saga issues the next command only after the previous reply.
- **Where ordering can break:** non-blocking retry topics. A record retried via `-retry-N` is processed after newer records of the same key. We accept this and make handlers order-tolerant:
  - saga replies are guarded by the current step (STALE otherwise)
  - a release that overtakes its reserve leaves a tombstone
  - capture on released funds is rejected (ADR-014, SAGA-DESIGN)

## Hot partitions

- **Cause:** skewed keys. A single paymentId cannot be hot (one payment produces about 10 messages). Hash collisions of many busy keys onto one partition can be.
- **Detect:** per-partition consumer lag and bytes-in (Kafka metrics), and `payflow.events.processing` by partition.
- **Fix without breaking ordering:**
  1. Add partitions only together with a planned drain: pause producers (outbox pause), let consumers drain, then add partitions.
  2. Or move to a new topic version with more partitions (dual-publish during migration).
  3. A merchant-level key would be the thing that creates hot spots, which is why we do not use it.

## Consumer parallelism

The maximum number of active consumers per group equals the partition count: 6 for each consumer group, so for example 6 `ledger-service` instances. More instances than partitions sit idle.

## Consequences

- Partition count is a capacity-planning decision reviewed per WP-03 load test.
- The key discipline is enforced in code (consumer validation) and in review.
