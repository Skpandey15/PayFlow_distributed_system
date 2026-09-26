# WP-02 Low-Level Design

## 1. What changed from WP-01

| WP-01 | WP-02 | Why |
|---|---|---|
| `POST /payments/{id}/authorize` and `/process` (synchronous, operator-driven) | **Removed.** Replaced by the asynchronous orchestrated saga started by `POST /payments` | Keeping a synchronous path would bypass funds control (M2) and preserve the dual write (M1) |
| Payment ACL adapters calling Fraud, Settlement and Ledger in-process | Replaced by commands and events over Kafka via outboxes | Temporal decoupling, durability, extraction readiness |
| `LoggingPaymentEventPublisher` | `OutboxPaymentEventPublisher` (same port, now durable) | M1 closed with no use-case change, as WP-01 designed |
| No funds control | `account_balance` + `funds_reservation`, deposits API | M2 |
| Manual re-drive | `SagaRecoveryService` scanner | M3 |
| `payments:process` scope, orchestrator client | Removed. New scopes: `funds:deposit`, `ops:dlq-replay`, `ops:metrics`; clients `payflow-treasury` and `payflow-ops` | Least privilege for the new capabilities |

Unchanged: the domain model and invariants, idempotent creation (WP-01 ADR-006), cancel and query semantics, security edge, persistence and money rules. The Account lookup at creation stays synchronous (a fast, local read-your-write check for good API errors). Reservation re-checks eligibility authoritatively, which closes the check-then-act gap (tested).

## 2. New / changed components

| Package | Component | Role |
|---|---|---|
| `contracts` | `EventCatalog`, `*Messages` records, `Topics`, `Producers` | Published language (JDK-only) |
| `platform.messaging` | `EventCodec`, `EnvelopeFactory`, `TraceparentSupplier`, `MessageContext` | Envelope building, decoding, context propagation |
| `platform.messaging.outbox` | `OutboxWriter`, `OutboxRelay`, `OutboxRelayScheduler`, `OutboxRelayFactory` | Transactional Outbox and polling publisher |
| `platform.messaging.inbox` | `InboxStore` | processed-event claims |
| `platform.messaging.consumer` | `EventConsumerSupport`, `IdempotentExecutor`, `EventProcessingInterceptor` | Consumer boundary: validate, context, classify, log once |
| `platform.messaging.error` | taxonomy + `FailureClassifier` | Retry vs DLT policy |
| `platform.messaging.dlq` | `DeadLetterObserver`, `DeadLetterReplayService` (+ controller) | DLT visibility and controlled replay |
| `platform.messaging` | `KafkaMessagingConfiguration`, `DirectEventPublisher`, `MessagingMetrics` | Topics, DLT sanitisation, metrics |
| `payment.domain.saga` | `PaymentSaga`, `SagaStep`, `CompensationReason`, `CheckoutContext` | Saga state machine |
| `payment.application` | `PaymentSagaService`, `SagaRecoveryService`, `SagaPolicy`, ports `SagaCommandPort`, `PaymentSagaRepositoryPort` | Orchestration and recovery |
| `payment.adapter.*.messaging` | `PaymentSagaListener`, `OutboxSagaCommandPublisher`, `OutboxPaymentEventPublisher` | Kafka in and out |
| `account.domain` | `AccountBalance`, `FundsReservation`, `ReservationStatus` | Funds model |
| `account.application` | `FundsService` (`FundsCommandUseCase`, `DepositFundsUseCase`) | Reserve, capture, release, deposit |
| `account.adapter.*` | `FundsCommandListener`, `OutboxFundsEventPublisher`, balance/reservation/deposit persistence | |
| `fraud.adapter.*.messaging` | `FraudCommandListener`, `KafkaRiskDecisionPublisher` | Consume-process-produce |
| `settlement.adapter.*.messaging` | `SettlementCommandListener`, `OutboxSettlementEventPublisher` | Resumable participant |
| `ledger.adapter.in.messaging` | `LedgerFundsListener` | Choreographed follower |

## 3. Data (Flyway V5–V7)

| Migration | Contents |
|---|---|
| V5 | `account.account_balance` (CHECK available ≥ 0, reserved ≥ 0; backfilled 0), `account.funds_reservation` (unique payment_id), `account.funds_deposit` |
| V6 | `outbox_event` in payment, account and settlement (BIGSERIAL order, unique event_id, partial index on unpublished); `processed_event` in payment, account and ledger (PK consumer + event_id) |
| V7 | `payment.payment_saga` (unique payment_id, step CHECK, partial in-flight index on step_started_at) |
| R__ | Runtime-role grants extended (DML only; ledger still INSERT/SELECT only; DELETE only for purges; sequence usage) |

## 4. Transaction boundaries

| Operation | Transaction contents | Outside |
|---|---|---|
| Create payment | payment + idempotency + saga + outbox(PaymentCreated, AssessPaymentRisk) | account lookup |
| Saga reply | inbox + saga + payment + outbox(command, events) | nothing |
| Reserve / capture / release | inbox + `FOR UPDATE` balances + reservation + outbox | nothing |
| Settlement submit | T1 find-or-create PENDING; T2 complete/decline + outbox | **rail call** |
| Fraud assess | MongoDB insert (single document) | **publish** (then offset commit) |
| Ledger follow | inbox + journal (deferred balance trigger) | nothing |
| Relay | advisory lock + mark published (bounded; includes Kafka sends, ADR-010) | |
| Recovery | SKIP LOCKED batch: saga + payment + outbox | |

## 5. Consumer configuration

- `ack-mode: record`, `enable-auto-commit: false`, `auto-offset-reset: earliest`, `isolation-level: read_committed`
- `max-poll-records: 50`, `concurrency: 3`
- `CooperativeStickyAssignor` (incremental rebalances)
- Listener observation enabled, so the trace is continued

**Offset ownership.** The consumer group owns its offsets. Spring's container commits them synchronously after each record's listener invocation returns, or after the record was successfully routed to a retry or DLT topic. We never commit before our database commit.

**Rebalance behaviour.** On revoke, the container commits processed offsets. In-flight records whose transaction did not commit are redelivered to the new owner, and the inbox absorbs the overlap. Verified by `ConsumerSemanticsIT.stoppedConsumerResumes…`: stop, then 10 events, then start, giving 10 applied exactly once.

## 6. Producer configuration

- `acks=all`, `enable.idempotence=true`, `max.in.flight=5`
- `max.block.ms=5000`, `request.timeout.ms=5000`, `delivery.timeout.ms=15000`, `linger.ms=5`
- Template observation disabled (we restore the stored traceparent instead)

## 7. Metrics (all under `/actuator/metrics`, scope `ops:metrics`)

| Metric | Question it answers |
|---|---|
| `payflow.outbox.backlog{outbox}` / `payflow.outbox.oldest.age.seconds{outbox}` | Is publication keeping up? Is Kafka reachable? |
| `payflow.outbox.published` / `publish.failures` | Publication throughput and failures |
| `payflow.events.consumed{topic,consumer,outcome}` | Throughput; duplicate / stale / ignored rates |
| `payflow.events.failed{category}` | What fails, retryable or not |
| `payflow.events.dead_lettered{topic,consumer,category}` | **Page someone** |
| `payflow.events.replayed` | Operator replays |
| `payflow.events.processing` (timer) | Consumer latency (p95/p99 SLOs in WP-03) |
| `payflow.saga.transitions{from,to}` / `payflow.saga.compensations` / `payflow.saga.recovery{action,step}` | Workflow health, compensation rate, stuck sagas |
| Kafka client metrics (`kafka.consumer.fetch.manager.records.lag.max`, …) | Consumer lag |

## 8. Debugging a stuck payment (runbook)

1. `GET /api/v1/payments/{id}` gives the status; `GET /api/v1/payments/{id}/saga` (payments:admin) gives the step, attempts, stepStartedAt and correlationId.
2. Search the logs by `correlationId` (or `sagaId`). You see every hop: outbox, consumer, saga transition, recovery.
3. Is an event stuck in an outbox? Query `select * from <ctx>.outbox_event where aggregate_id = … and published_at is null`. The `last_error` column says why; the backlog metric says how many.
4. Is an event in a DLT? The `payflow.events.dead_lettered` alert, then the DLT record headers give category, code and the original coordinates. Fix the cause, then `POST /api/v1/ops/dead-letters/replay`.
5. Saga in MANUAL_REVIEW: query the rail by idempotency key = paymentId. Then either re-issue settlement (resume) or confirm the decline and release.
