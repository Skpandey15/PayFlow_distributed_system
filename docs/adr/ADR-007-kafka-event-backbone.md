# ADR-007: Apache Kafka (KRaft) as the event backbone

- Status: Accepted (WP-02)
- Date: 2026-09-27

## Context

WP-01 left three accepted MAJOR findings that all come from synchronous, in-process orchestration:

| Finding | Problem |
|---|---|
| M1 | Dual write between the payment commit and the ledger posting |
| M2 | No funds reservation across the Payment and Account contexts |
| M3 | No automatic recovery |

The contexts must also become separately deployable later, without a rewrite. We need a durable, replayable, ordered-per-key log that decouples producers from consumers in time and availability.

## Decision

- Use **Apache Kafka 4.2 in KRaft mode** (no ZooKeeper).
- Topics are grouped per context: `<ctx>.commands` owned by the receiver and `<ctx>.events` owned by the producer (ADR-008).
- Producers are idempotent with `acks=all`. Consumers commit offsets per record, after processing, with auto-commit off.
- Spring Kafka is the client. It lives only in adapters and `platform.messaging`, and ArchUnit keeps Kafka out of the domain and application layers.

## Alternatives

| Option | Why not chosen |
|---|---|
| **Synchronous REST between services** | Temporal coupling: every participant must be up at the same time. Retries become the caller's problem, and the dual write stays. It is fine for queries; we keep the Account lookup synchronous at payment creation. |
| **RabbitMQ** | Excellent for work queues and routing. But messages are deleted on ack, so there is no replay, and consumers cannot rewind to rebuild state or audit. Per-key ordering is weaker once competing consumers are involved. Kafka's retained, partitioned log fits event-carried state, audit and replay. |
| **Cloud queues (SQS/SNS)** | No per-key ordering across consumers without FIFO limits, no replay window beyond the DLQ, and lock-in before the EKS WP. |
| **PostgreSQL as a queue (LISTEN/NOTIFY, SKIP LOCKED)** | Simple and transactional. But it couples every context to one database, which contradicts ADR-004, and it does not scale out consumers or retention. |

## Trade-offs

We gain:
- durability
- replay
- per-key ordering
- consumer-group fan-out
- producer/consumer independence (a consumer outage does not stop payment creation)

We accept:
- eventual consistency
- at-least-once delivery, so every consumer must be idempotent
- a new stateful system to operate: brokers, partitions, lag, retention
- more moving parts in tests (Testcontainers)

## Failure implications

| Failure | Consequence |
|---|---|
| Broker down | Producers write to the outbox (no loss) and the backlog grows (metric). Consumers pause. The API keeps accepting payments. Verified live. |
| Consumer down | Lag grows. It resumes from committed offsets (`ConsumerSemanticsIT`). |
| Partition leader failover | `acks=all` + `min.insync.replicas=2` (production) → no acknowledged write is lost. |

## Operational consequences

- Production needs 3+ brokers, RF=3 and `min.insync.replicas=2` (local: 1/1).
- Monitor consumer lag, outbox backlog and age, and the DLT rate.
- Retention of 7 days bounds the replay window.
- Readiness does **not** include Kafka, by design: the outbox absorbs broker outages.
