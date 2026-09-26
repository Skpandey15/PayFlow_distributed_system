# WP-02 Architecture Technical Review: Distributed Event-Driven Processing

- Date: 2026-09-27
- Scope: all WP-02 code, migrations V5–V7, configuration, compose runtime, contracts, tests and documentation
- Method: challenge every decision on why, why not, consistency guarantee, crash points, duplication, loss, ordering, replay, idempotency, observation and recovery. Verified against the running system, not only against the code.

## Evidence base

| Evidence | Result |
|---|---|
| `./gradlew clean build` | **BUILD SUCCESSFUL: 217 tests, 0 failed, 0 skipped.** 108 unit, 33 architecture, 76 integration on real PostgreSQL 18.1, MongoDB 8.0, Kafka 4.2 KRaft |
| ArchUnit | 26 rules (6 new for messaging); 7 negative tests prove the rules catch Kafka in the domain, listeners outside inbound adapters, and controllers publishing |
| Contract governance | 28 contract tests: a schema for every type and version; strict producer validation; backward compatibility incl. a negative test; upcast v1→v2; tolerant reader |
| Failure engineering | All 18 required scenarios plus 3 extra, see `docs/failures/WP-02-FAILURE-MATRIX.md` |
| Live stack (compose + Keycloak) | Treasury deposit 201, customer deposit 403; async payment SETTLED, saga COMPLETED; balances 150.00 = ledger 150.00, payee 50.00; over-balance REJECTED INSUFFICIENT_FUNDS |
| Live Kafka outage | Payment accepted (201 CREATED) with the broker down; backlog 2, oldest age 5 → 15 s, publish failures 2 → 4, readiness UP; broker back → SETTLED, backlog 0 |
| Live structured logs | Consumer lines carry traceId, spanId, correlationId, causationId, eventId, sagaId, topic, partition, offset, consumerGroup, retryAttempt |

## Resolved during WP-02 (found by tests or review before completion)

| ID | Was | Finding | Resolution |
|---|---|---|---|
| R1 | MAJOR | The saga wrote the next command to the outbox **before** its versioned UPDATE. Still correct thanks to the optimistic lock, but the ordering argument depended on subtle reasoning | Transitions now return the commands to issue. State is persisted first and emitted after (orchestrator and recovery). `PaymentSagaServiceTest.stateIsPersistedBeforeCommandsAreIssued` |
| R2 | BLOCKER | The recovery service kept per-call state in a field of a singleton, a race under concurrent runs | Replaced by an immutable per-saga `Recovery` record |
| R3 | MAJOR | A concurrent duplicate `ReserveFunds` would have hit the unique constraint, been classified DATA_INTEGRITY and dead-lettered a legitimate duplicate | Lock the balance first, then re-read the reservation. The duplicate re-announces instead (`FundsConcurrencyIT.concurrentDuplicateReservation…`) |
| R4 | MAJOR | Capture of a tombstone reservation (payee null) would have thrown an NPE (UNKNOWN, retried 3×) instead of a business rejection | State check before locking: permanent BUSINESS_RULE → DLT |
| R5 | MAJOR | Outbox gauges held a weak reference and could silently read NaN | `strongReference(true)`; verified live via `/actuator/metrics` |
| R6 | MAJOR | DLT diagnostics read `kafka_dlt-original-*` headers, which Spring Kafka 4 does not set (it sets `kafka_original-*`), so the original coordinates logged as null | Header names corrected; asserted in `ConsumerSemanticsIT` |
| R7 | MINOR | The settlement decline reason lost its `SETTLEMENT_DECLINED:` prefix (API contract drift from WP-01) | Prefix applied in the orchestrator; asserted |

**Open BLOCKERs: none.**

## Open MAJOR findings (explicitly accepted)

| ID | Finding | Risk | Justification | Mitigation now | Owner |
|---|---|---|---|---|---|
| **K1** | **Kafka authorisation not enforced.** Local Kafka is PLAINTEXT without an authorizer, and the modular monolith uses one client identity for every context. The ACL matrix exists as code (`deploy/kafka/acl-matrix.sh`) but is not applied | A compromised component could write any topic | Per-service principals only make sense once contexts deploy separately; a shared identity cannot be least-privilege | (1) Producer-side ownership check: `EnvelopeFactory` refuses types the context does not own. (2) Consumer-side check of producer and topic: forged events are dead-lettered UNTRUSTED_SOURCE (tested). (3) ArchUnit: only `platform.messaging` touches `KafkaTemplate` | Platform / extraction WP (SASL-SCRAM or mTLS, ACLs, NetworkPolicies) |
| **K2** | **Relay throughput ceiling.** One active relay per outbox (advisory lock); sequential, synchronous sends inside the relay transaction | About 1–2k msgs/s per outbox; outbox age grows at 10× | Correct per-key ordering with no extra infrastructure (ADR-010) | Backlog and age metrics; pause/resume; batch 100 | WP-03: async batched sends with per-partition stop, or Debezium CDC |
| **K3** | **MANUAL_REVIEW has no tooling.** Escalated sagas need an operator, and there is no resolve API (re-issue or confirm decline) | Payments stay PROCESSING with funds held until a human acts | Deliberately safe (never double spend); needs product and ops design | ERROR log + `saga.recovery{ESCALATED_TO_MANUAL_REVIEW}` metric; runbook in WP-02-LLD §8 | WP-03 (ops tooling, alert routing) |
| **K4** | **No continuous reconciliation** between ledger and `account_balance` | Drift (e.g. a DLT'd `FundsCaptured` never replayed) is detected only by the DLT alert, not by the numbers | The invariant is proven in tests and live, but not monitored | DLT alerting; per-message idempotent replay | WP-03 reconciliation job + SLO |
| M4 (WP-01) | Schema isolation by convention (shared runtime DB role) | unchanged | unchanged | ArchUnit; no cross-schema access; outbox and inbox per schema keep extraction mechanical | Extraction WP |

WP-01 findings closed by WP-02: **M1** (dual write, via the Outbox), **M2** (funds control, via reservation), **M3** (automatic recovery, via the saga scanner).

## MINOR

| ID | Finding | Plan |
|---|---|---|
| m1 | Non-blocking retries reorder records of one key | Handled by order-tolerant handlers (step guards, tombstone, capture rejects released funds). Documented in ADR-008/014 |
| m2 | Inbox retention 7 days: replays older than that rely on natural keys | Every inbox consumer also has a natural key or guard. Keep retention ≥ topic retention |
| m3 | Ledger journal race nested in the inbox transaction surfaces as UNKNOWN and is retried, instead of being handled in place | Self-heals on retry (journal found). Refine with a savepoint or a dedicated translation |
| m4 | `payment.events` has no internal consumer | Intentional public stream; the first consumer is notifications or read models |
| m5 | Changing the partition count remaps keys | Only with a drain (ADR-008) |
| m6 | The recovery batch is one transaction, so one failing saga delays the batch until the next run | Per-saga transactions if it ever occurs in practice |
| m7 | Fraud publishes synchronously per command (adds 5–20 ms per assessment) | Acceptable; batching is WP-03 |
| m8 | New consumer groups start at `earliest` and reprocess retained history | Safe by idempotency; document it for analytics groups |
| m9 | Tests share one context, so saga work from a previous test continues in the next | Tests are written to be tolerant (observed once: the oldest-unpublished-row assertion) |
| m10 | Deposits have no limits or four-eyes approval | Treasury process is out of scope; scope-restricted client only |

## OBSERVATIONS

- **O1: Is Kafka necessary?** At today's volume a PostgreSQL queue (SKIP LOCKED) would work. Kafka is justified by replay (rebuild, audit), fan-out to independent groups, per-key ordering at scale, and the extraction roadmap, **not** by current throughput. Accepted consciously.
- **O2: Redis not introduced.** No WP-02 requirement needed it. Dedup, locking and funds correctness all live in PostgreSQL, in the same transaction as the effect, which Redis cannot match.
- **O3: Schema Registry deferred** with explicit triggers (ADR-011). The build currently enforces BACKWARD_TRANSITIVE rules.
- **O4: CQRS not introduced.** No cross-context query yet; `payment.events` enables it later.
- **O5: Operational complexity rose sharply.** Outboxes, relays, 7 main topics plus 4 retry/DLT topics per (topic, group), a saga scanner, inboxes and DLQ tooling. This is the price of M1–M3; the metrics and runbook reduce it.

## Decision challenges (abridged)

| Decision | Guarantee | Crash point | Duplicate? | Lost? | Reordered? | Replay? | Observed by |
|---|---|---|---|---|---|---|---|
| Outbox | state ⇔ event atomic | after commit | yes (relay) | no | no (single writer, id order) | yes (7 d topic) | backlog, age |
| Record acks after the DB commit | at least once | after commit, before offset | yes | no | within partition no | yes | DUPLICATE count |
| Inbox + natural keys | effectively once | concurrent duplicate | absorbed | — | — | safe | outcome metric |
| Retry topics | bounded, non-blocking | retry exhaustion | — | no (DLT) | **yes** (tolerated) | replay endpoint | failed, dead_lettered |
| Orchestrated saga | eventual; compensation where the outcome is known | any step | stale ignored | no | guarded | recovery re-issue | transitions, recovery |
| Pessimistic funds lock | no overdraw | mid-transaction | — | — | serialized | — | CHECK constraint backstop |

## What breaks first at 10× traffic

1. **Outbox relay throughput (K2)** for `payment.outbox_event`. Every payment emits about 9 messages from that outbox.
2. **PostgreSQL write amplification.** About 6 transactions and 20 inserts or updates per payment (outbox + inbox + saga + balances). WAL, vacuum and index bloat on outbox and inbox; purges become significant.
3. **Hot payer accounts.** Row lock serialisation on `account_balance`.
4. **Consumer parallelism** is capped at 6 partitions per group.
5. **Synchronous fraud publish** and MongoDB velocity counts.

## Assumptions to validate under load (WP-03)

- Relay lag at peak.
- Lock wait on hot accounts.
- Retry-topic volume during a dependency blip (storm size).
- Rebalance duration with cooperative-sticky assignment.
- End-to-end p99 from create to SETTLED.
- Inbox and outbox purge cost.

## Final Evidence Matrix

| Requirement | Architecture | Implementation | Test / failure evidence | Review finding | Status |
|---|---|---|---|---|---|
| Kafka without violating Clean Architecture | ADR-007, EVENT-ARCHITECTURE | ports + `adapter.*.messaging` + `platform.messaging` | ArchUnit `core_does_not_know_messaging`, `kafka_listeners_live_in_inbound_messaging_adapters`, `only_the_platform_touches_the_kafka_producer`, `web_controllers_never_publish_events` + negative fixtures | — | PASS |
| KRaft local Kafka | ADR-007 | compose `apache/kafka:4.2.0` KRaft; Testcontainers KRaft | compose healthy; all ITs | — | PASS |
| Versioned contracts | ADR-011, EVENT-CONTRACTS | `contracts` + 22 schemas | `EventContractTest` (28) | O3 | PASS |
| Topic design | ADR-008, KAFKA-TOPIC-CATALOG | `KafkaMessagingConfiguration` | topics created in all ITs | m5 | PASS |
| Intentional partitioning | ADR-008 | key = paymentId; key == aggregateId enforced | `OutboxIT.lifecycleEvents…` (one partition, causal order) | m1 | PASS |
| Consumer groups | KAFKA-TOPIC-CATALOG | 5 groups, per-group retry/DLT | `ConsumerSemanticsIT` (ledger vs payment groups isolated) | — | PASS |
| Offset strategy | ADR-013, WP-02-LLD §5 | AckMode.RECORD, auto-commit off | `crashAfterCommitBeforeOffsetCommit…`, `stoppedConsumerResumes…` | — | PASS |
| Transactional Outbox | ADR-009 | `OutboxWriter` (transaction required) | `OutboxIT` (6) | — | PASS |
| Dual-write gap closed (M1) | ADR-009 | outbox per context; ledger as follower | `eventsCommittedWhilePublisherIsDown…`, live outage | — | PASS |
| Duplicate publication safe | ADR-009/013 | inbox | `republishedEventIsDeduplicated…` | — | PASS |
| Idempotent consumers | ADR-013 | inbox + natural keys + guards | `duplicateEvent…`, `concurrentDuplicateReservation…` | m2, m3 | PASS |
| Saga | ADR-012, SAGA-DESIGN | `PaymentSaga`, `PaymentSagaService` | `PaymentApiIT`, `PaymentSagaTest`, `PaymentSagaServiceTest` | — | PASS |
| Funds reservation (M2) | ADR-015 | balances + reservations + FOR UPDATE + CHECK | `FundsConcurrencyIT` (7), `FundsDomainTest` | K4 | PASS |
| Compensation | SAGA-DESIGN | ReleaseFunds, tombstone | `settlementDecline…`, `cancelWhileReservation…`, `duplicateRelease…`, `releaseBeforeReserve…` | — | PASS |
| Stuck-workflow recovery (M3) | SAGA-DESIGN | `SagaRecoveryService` + job | `fraudStoreOutage…`, `fundsReservationTimeout…`, `unknownSettlementOutcome…` | K3 | PASS |
| Retry architecture | ADR-014 | classified, bounded, non-blocking | `crashBeforeCommit…`, `retryExhaustion…` | m1 | PASS |
| DLQ | ADR-014 | per-group DLT, sanitized, observer, replay API | `poison…`, `retryExhaustion…` (replay twice) | — | PASS |
| Poison messages | ADR-014 | DESERIALIZATION → DLT | `poisonMessage…` (partition not blocked) | — | PASS |
| Schema evolution tested | ADR-016 | RiskAssessed v1→v2 + upcaster | `EventContractTest` | — | PASS |
| Zero Trust Kafka (WP-02 scope) | ZERO-TRUST §6, acl-matrix.sh | producer and consumer ownership checks; ACL matrix as code | `eventFromUnauthorizedProducerIsRejected` | **K1** | PASS (scope-limited; enforcement accepted MAJOR) |
| Trace context propagated | EVENT-ARCHITECTURE §3 | stored traceparent + listener observation | `relayPublishesTraceAndCorrelationContext…`; live log line | — | PASS |
| Structured event logging | LOGGING-STANDARD | MDC + key-values at boundaries | live ECS line | — | PASS |
| Async exception taxonomy | EXCEPTION-ARCHITECTURE | `FailureCategory`, classifier | `FailureClassifierTest` (5) + DLT categories in ITs | — | PASS |
| Failure scenarios tested | WP-02-FAILURE-MATRIX | — | 18/18 + 3 extra | m9 | PASS |
| ArchUnit extended | CLEAN-ARCHITECTURE | 26 rules | 33 architecture tests | — | PASS |
| ADRs | ADR-007…016 | — | 10 ADRs | — | PASS |
| Review / interview docs | this doc; WP-02-INTERVIEW-DEFENSE | — | — | — | PASS |
| All tests pass | — | — | 217 / 0 failed | — | PASS |
| Redis | EVENT-ARCHITECTURE §7 | not introduced (no requirement) | — | O2 | N/A (justified) |
| Schema Registry | ADR-011 | deferred, triggers defined | — | O3 | DEFERRED (justified) |

## Verdict

**WP-02 is complete.**
- No BLOCKER remains open.
- Four MAJOR findings (K1–K4) are accepted with risk, mitigation and owner.
- WP-01 M1, M2 and M3 are closed with test and live evidence. WP-01 M4 is carried forward.
