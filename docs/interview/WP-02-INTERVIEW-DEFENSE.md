# WP-02 Principal Engineer Interview Defense: Distributed Event-Driven Processing

Concise answers, each anchored in PayFlow's implementation and evidence.

## Kafka and messaging

**Why Kafka?**
We needed a durable, replayable, ordered-per-key log that decouples contexts in time and availability. Payment creation must not depend on Fraud, Settlement or Ledger being up. Kafka gives:
- retention (replay, audit)
- consumer groups (independent fan-out)
- per-partition ordering
- scale-out consumers

The honest caveat: at today's volume a PostgreSQL queue would work. Kafka is chosen for replay, fan-out and the extraction roadmap (review O1).

**Why not synchronous REST everywhere?**
- Temporal coupling: every participant must be up simultaneously.
- Latency adds up along the chain.
- Retries become the caller's problem.
- The dual write between "my commit" and "your call" remains.

We keep REST for queries (the account lookup at creation) and use events for state changes.

**Kafka vs RabbitMQ?**
RabbitMQ is a broker with queues: messages are deleted on ack, and routing and priorities are excellent. Kafka is a retained, partitioned log: consumers track offsets and can rewind, and many groups read independently. For financial events we want replay and per-payment ordering, so Kafka.

**What is a partition?**
An ordered, append-only log that is a shard of a topic. It is the unit of ordering, parallelism and replication. Records with the same key go to the same partition (hash of the key).

**Why paymentId as the partition key?**
- Every message about one payment lands on one partition, so reserve precedes capture or release.
- It spreads load evenly, because a payment has about 10 messages.
- AccountId would order all of an account's payments (not needed) and create merchant hot spots.
- Consumers enforce `key == aggregateId`.

**Maximum consumer parallelism?**
The number of partitions per consumer group: 6 in production config. A seventh instance of the same group sits idle.

**What causes a hot partition, how do you detect it, how do you fix it without breaking ordering?**
- **Cause:** key skew, or many busy keys hashing together.
- **Detect:** per-partition lag and bytes-in, and consumer latency by partition.
- **Fix:** don't add partitions live, because it remaps keys and breaks ordering during the change. Either drain first (pause producers with the outbox pause, let consumers catch up, then add partitions), or migrate to a new topic with more partitions via dual-publish.

**Who owns the offset? When should it be committed?**
The consumer group owns it. We commit **after** the business transaction committed (AckMode.RECORD, auto-commit off), never before and never on a timer.

**DB commit succeeded, offset commit failed?**
Kafka redelivers. The inbox (`processed_event`, same transaction as the effect) recognises the eventId, so the result is DUPLICATE with no second effect (`ConsumerSemanticsIT.crashAfterCommitBeforeOffsetCommit…`).

**Can Kafka duplicate events? Lose them?**
- **Duplicate:** yes. Producer retries without idempotence, relay re-sends, redelivery after a crash or rebalance.
- **Lose:** only with weak settings: `acks=1`, auto-commit before processing, unclean leader election, or retention expiring before consumption.

We use `acks=all`, the idempotent producer, `min.insync.replicas=2` in production, and commit after processing.

**What delivery guarantee does PayFlow provide?**
At-least-once delivery with effectively-once business effects: an inbox on eventId, natural business keys (one reservation, settlement, journal and assessment per payment) and saga step guards.

**Why not claim exactly-once payments?**
Kafka's exactly-once covers Kafka-to-Kafka processing. Our effects happen in PostgreSQL, MongoDB and an external rail, and none of them take part in Kafka transactions. Anyone claiming end-to-end exactly-once is hiding the idempotency and reconciliation that actually provide it.

## Dual write and Outbox

**What is the dual-write problem?**
Updating two systems (database and broker) without a shared transaction:
- Commit, then crash before publish: the event is lost.
- Publish, then rollback: a phantom event.

**How does the Transactional Outbox solve it?**
The event is inserted into `outbox_event` in the **same local transaction** as the state change. A relay publishes committed rows later. State and event are atomic, and publication is retried until the broker acknowledges.

**Can the outbox still publish duplicates?**
Yes. If the relay crashes after the broker ack but before marking the row, it re-sends it. That is by design: consumers dedupe on eventId (`OutboxIT.republishedEventIsDeduplicated…`).

**How do consumers handle duplicates?**
Three layers:
1. Inbox claim in the same transaction.
2. Natural unique business keys (for re-issued commands with new ids).
3. State-machine guards for stale or late replies.

**Polling outbox vs CDC/Debezium?**
- **Polling (ours):** no new infrastructure; single writer per outbox via an advisory lock; publishes in id order and stops at the first failure; about 200 ms latency; throughput ceiling around 1–2k/s per outbox.
- **CDC:** reads the WAL; lower latency, higher throughput; but Kafka Connect, replication slots and WAL bloat risk.

We start with polling. CDC is the production-scale path, and the outbox table stays the same contract (ADR-010).

## Consistency, Saga, compensation

**What is eventual consistency here?**
Each context is strongly consistent internally. Across contexts, state converges once events are processed. A payment is SETTLED only after funds were captured. The ledger follows funds within about a second, and the invariant `ledger == available + reserved` holds after catch-up (verified live).

**What is a Saga? Saga vs 2PC?**
A saga is a sequence of local transactions, each publishing the trigger for the next, with compensating actions for failures.
- 2PC locks resources across services and blocks if the coordinator fails. MongoDB and the rail cannot take part anyway.
- A saga gives up isolation (intermediate states such as RESERVED are visible) in exchange for availability and autonomy.

**Choreography vs orchestration?**
- **Choreography:** services react to each other's events; the flow is implicit.
- **Orchestration:** one coordinator with an explicit state machine.

We orchestrate the payment (explicit compensation, timeouts, "where is my payment?" is a query) and choreograph the Ledger follower.

**What is compensation?**
A new forward business action that semantically undoes an earlier committed one. It is not a rollback. `ReleaseFunds` returns a hold, is idempotent and is order-safe (tombstone).

**What happens when compensation fails?**
1. Bounded retries.
2. Then the DLT.
3. The saga stays COMPENSATING, and recovery re-issues ReleaseFunds.
4. After the budget: MANUAL_REVIEW, with an ERROR log and a metric.

Funds stay held, which is a safe state, and an operator resolves it.

**Why never auto-compensate a settlement timeout?**
The rail may already have moved the money. Releasing the hold would allow a double spend. The case is escalated instead (`unknownSettlementOutcomeIsEscalatedNotCompensated`).

**How do you prevent double funds reservation?**
- Every reservation locks the balance row (`SELECT … FOR UPDATE`) and re-reads the payment's reservation after the lock.
- The reservation has a unique `payment_id`.
- `CHECK (available >= 0)` is the backstop.

Evidence: 10 concurrent reservations of 30.00 against 100.00 give exactly 3 successes, three runs in a row.

## Retry, DLQ, poison, replay

**Retry vs DLQ?**
- Retry transient failures (database, MongoDB, rail, concurrency): bounded and non-blocking, 1 s, 3 s, 9 s.
- Permanent failures (malformed, unsupported version, contract, untrusted, business rule) go straight to the DLQ.
- Retries that are exhausted also end in the DLQ.

**What is a poison message?**
A message that can never succeed, such as unparseable JSON or an unknown version. It is detected at the consumer boundary, classified permanent, dead-lettered immediately and alerted. The partition keeps flowing (tested: the next event on the same key was processed).

**Who owns a DLQ?**
The consuming context's team. The topic name encodes the group: `funds.events-ledger-service-dlt`. Platform on-call owns the `dead_lettered` alert.

**How do you replay DLQ events safely?**
1. `POST /ops/dead-letters/replay` (scope `ops:dlq-replay`, audited).
2. It re-publishes to the failed group's own `-retry-0` topic (other groups are unaffected), with the original eventId.
3. The inbox makes a second replay a no-op (tested: replayed twice, applied once).

## Schemas

**How do schemas evolve?**
- Additive only within a type: new optional fields become a new version. Consumers upcast old versions and ignore unknown fields.
- Breaking changes get a new type.
- Deploy readers before writers.

The build enforces backward compatibility, and a negative test proves the checker catches breaking changes.

**Why (not yet) a Schema Registry?**
All producers and consumers share one build, and JSON Schemas are checked by tests: strict producers plus a compatibility check. A registry adds a runtime dependency for a guarantee we already have. It arrives when the first context is extracted or an external consumer appears, in BACKWARD_TRANSITIVE mode (ADR-011).

## Failures, rebalance, security, debugging

**What happens during a consumer rebalance?**
- Partitions are reassigned; cooperative-sticky assignment is incremental.
- Processed offsets are committed on revoke.
- Records not yet committed are redelivered to the new owner, and the inbox absorbs the overlap (tested by stopping and restarting a listener).

**What happens if Kafka is unavailable?**
The API still accepts payments. Events accumulate in the outboxes (backlog and age metrics grow) and readiness stays UP. When Kafka returns, the relays drain in order and the sagas complete. Verified live.

**How does Zero Trust apply to Kafka?**
- No trust by network location.
- Each service gets its own principal (SASL/mTLS) and least-privilege ACLs: a single writer per topic, and read only on what it consumes (`acl-matrix.sh`).
- Consumers also validate that the producer owns the event type and topic. A forged event is dead-lettered UNTRUSTED_SOURCE.
- Personal data is only on `fraud.commands`.

Today's gap is honest: local PLAINTEXT and one shared identity in the monolith (K1).

**How do you trace one payment across asynchronous services?**
- The `traceparent` is captured when the outbox row is written, restored on the record, and continued by the listener's observation.
- The `correlationId` is carried in every envelope and log line.
- Search the logs by correlationId, or query `GET /payments/{id}/saga`.

**correlationId vs traceId vs eventId vs causationId vs sagaId?**

| Id | Scope |
|---|---|
| traceId | Technical trace (spans, timing) |
| correlationId | The originating business request, constant for the whole payment |
| eventId | One message; the dedup key |
| causationId | Which event caused this one (the chain) |
| sagaId | The workflow instance |

**How do you debug a payment that became stuck?**
1. The saga view gives the step, attempts and correlationId.
2. The logs by correlationId show the last hop.
3. An unpublished outbox row? Look at `last_error` and the backlog metric.
4. A DLT record? Check the headers for category and code, fix, then replay.
5. MANUAL_REVIEW? Query the rail by idempotency key = paymentId, then resolve.

(WP-02-LLD §8.)
