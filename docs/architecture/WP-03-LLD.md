# WP-03 Low-Level Design: resilience, scale and production engineering

Scope: what WP-03 added or changed on top of WP-01/WP-02. For the unchanged designs see WP-01-LLD and WP-02-LLD.
Decisions: ADR-017 … ADR-024.

## 1. Module map (new and changed)

```
com.payflow
├── platform
│   ├── messaging
│   │   ├── KafkaLagMonitor                      NEW  broker-side lag (AdminClient) → payflow.kafka.consumer.lag
│   │   ├── MessagingMetrics                     +time()  (histogram timers)
│   │   ├── MessageContext                       +dependency, circuitBreakerState, deliveryOutcome keys
│   │   └── outbox
│   │       ├── OutboxRelay                      CHANGED  sendPipelined / sendSequentially (A/B), batch UPDATE,
│   │       │                                            publish-delay & ack-wait timers, oldestUnpublishedAge()
│   │       ├── OutboxRelayScheduler             CHANGED  bounded drain loop (drain-budget)
│   │       └── OutboxProperties                 +pipelined, drainBudget
│   ├── web.traffic                              NEW  TrafficControlInterceptor (per-subject 429, admission 503),
│   │                                                 SubjectRateLimiters (Resilience4j RateLimiter per subject)
│   └── security.SecurityConfiguration           +/actuator/prometheus, ops:manual-review, ops:reconciliation routes
├── payment
│   ├── domain.saga.PaymentSaga                  +escalatedFrom, resumeFromManualReview(); SagaStep.isTerminal()
│   ├── application
│   │   ├── port.in  ManualReviewUseCase, SagaMonitoringUseCase                        NEW
│   │   ├── port.out CommandPublicationHealthPort, SettlementEvidencePort, ManualReviewAuditPort   NEW
│   │   └── usecase  ManualReviewService, SagaMonitoringService                        NEW
│   │               SagaRecoveryService          CHANGED  holds while commands are unpublished
│   │               PaymentSagaService           SagaTransition carries saga/step start times
│   ├── adapter
│   │   ├── in.web.ManualReviewController        NEW  /api/v1/ops/manual-reviews
│   │   ├── in.messaging.PaymentSagaListener     +payflow.saga.step.duration / .completion timers
│   │   ├── out.settlement.SettlementContextAdapter   NEW  ACL to SettlementOperationsUseCase
│   │   ├── out.messaging.OutboxCommandPublicationHealth NEW
│   │   └── out.persistence  JdbcManualReviewAuditAdapter (append-only), review queue + open-step queries
│   └── infrastructure  SagaMetricsJob NEW; SagaRecoveryJob logs hold transitions; SagaProperties +holdAge, +railIdempotencyWindow
├── settlement
│   ├── domain.Settlement                        +submissionAttempts, lastAttemptOutcome, lastErrorCode, lastAttemptAt
│   ├── application
│   │   ├── port.out.SettlementGatewayPort       CHANGED  +inquire, +voidInstruction, DeliveryOutcome, InstructionRejectedException
│   │   ├── port.in.SettlementOperationsUseCase  NEW  evidence(), voidAtRail()
│   │   └── usecase  SubmitSettlementService records unanswered attempts; SettlementOperationsService NEW
│   ├── adapter.out.gateway  HttpSettlementRailGateway, RailCallMetrics  NEW (Simulated*Gateway REMOVED)
│   └── infrastructure  SettlementRailConfiguration, SettlementRailProperties  NEW
└── reconciliation                               NEW bounded context (ADR-021)
    ├── domain  ReconciliationCheck, Severity, Finding
    ├── application  ReconciliationUseCase, ReconciliationService, ports ReconciliationSourcePort, MismatchStorePort
    ├── adapter.out.persistence  JdbcReconciliationSource (REPEATABLE READ snapshot), JdbcMismatchStore
    ├── adapter.in.web.ReconciliationController   /api/v1/ops/reconciliation
    └── infrastructure  ReconciliationConfiguration, ReconciliationJob (metrics)
rail-simulator (Gradle subproject)              NEW  external rail test double, JDK-only HTTP server
```

## 2. Data model changes (Flyway)

| Migration | Change |
|---|---|
| V8 | `settlement.settlement` + `submission_attempts`, `last_attempt_outcome` (ANSWERED / NOT_SENT / UNKNOWN), `last_error_code`, `last_attempt_at` |
| V9 | `payment.payment_saga.escalated_from` (+ check: MANUAL_REVIEW ⇒ not null); partial index `ix_payment_saga_manual_review`; `payment.manual_review_decision` (append-only, unique idempotency key) |
| V10 | schema `reconciliation`: `run`, `mismatch` (partial unique index on OPEN (check, subject)) |
| R (grants) | runtime role: INSERT/SELECT only on the decision table; its own schema for reconciliation |

## 3. Key sequences

### 3.1 Settlement submission with resilience

```
SettlementCommandListener ─▶ SubmitSettlementService
  TX1 find-or-create Settlement(PENDING)
  HttpSettlementRailGateway.submit
     Retry(max 2, 200 ms×2ⁿ ±50 %) ─▶ CircuitBreaker(rail) ─▶ Bulkhead(16, no wait) ─▶ POST /rails/{rail}/transfers
       200 ACCEPTED/DECLINED ─────────────────────────────────▶ TX2 complete/decline + outbox SettlementCompleted/Declined
       connect refused / 503 ─ retried once ─▶ still failing ─▶ GatewayUnavailable(NOT_SENT | UNKNOWN)
       timeout / 5xx / reset ────────────────────────────────▶ GatewayUnavailable(UNKNOWN)   (no immediate retry)
       circuit open / bulkhead full ─────────────────────────▶ GatewayUnavailable(NOT_SENT)  (not held against the rail)
  on GatewayUnavailable: TX recordUnansweredAttempt(outcome, code) ; rethrow → classified TRANSIENT → retry topic
```

### 3.2 Pipelined relay

```
@Scheduled relayAll: for each outbox: repeat publishBatch() while the batch was full and within drainBudget
publishBatch (one tx, advisory xact lock):
  rows = SELECT … WHERE published_at IS NULL ORDER BY id LIMIT 100
  futures = rows.map(kafka.send)                 # producer batches per partition; idempotent sequencing
  await all within sendTimeout                   # acked set, first failure
  UPDATE … SET published_at = now() WHERE id = ANY(acked)
  record first failure (attempts, last_error)    # failed rows lead the next scan
```

### 3.3 Admission control and rate limiting (HandlerInterceptor, after authentication)

```
POST /api/v1/payments:
  oldestUnpublishedAge (max over outboxes, cached 1 s) > 60 s ?  → 503 PAYMENTS_TEMPORARILY_UNAVAILABLE, Retry-After 30
  subject bucket (20/s) empty ?                                   → 429 RATE_LIMITED, Retry-After 1
POST /api/v1/ops/**: subject bucket (10/min) empty ?              → 429
```

### 3.4 Manual review: CONFIRM_NOT_SETTLED

```
ManualReviewController ─▶ ManualReviewService.decide
  audit.findByIdempotencyKey ─▶ replay?            (same key + same decision → return stored result)
  saga in MANUAL_REVIEW, escalatedFrom = AWAITING_SETTLEMENT ?
  settlement.voidAtRail(paymentId)  (inquiry bulkhead, no retry)  → ACCEPTED ? 409 RAIL_REPORTS_SETTLED
  TX: saga.resumeFromManualReview → AWAITING_SETTLEMENT (optimistic version); audit.append(evidence); outbox SubmitSettlement
  … rail answers DECLINED(VOIDED) → SettlementDeclined → saga COMPENSATING → ReleaseFunds → FAILED
```

### 3.5 Reconciliation run

```
ReconciliationJob (5 min) ─▶ ReconciliationService.run
  source.evaluate(now − 2 min): one REPEATABLE READ READ ONLY tx, pg_try_advisory_xact_lock
     9 checks: count(*) + findings query each
  store.recordRun: insert run; upsert OPEN mismatch per (check, subject) (times_seen++); resolve not-seen
  job: refresh gauges from openSummary (no ids as labels)
```

## 4. Configuration reference (new)

| Property | Default | Meaning |
|---|---|---|
| `payflow.settlement.rail.base-url` | http://localhost:8090 | rail endpoint |
| `…rail.connect-timeout` / `submit-timeout` / `inquiry-timeout` | 500 ms / 2 s / 1 s | HTTP timeouts |
| `…rail.retry.*` | 2 attempts, 200 ms, ×2, jitter 0.5 | Resilience4j retry |
| `…rail.circuit-breaker.*` | 20 / 10 / 50 % / 1.5 s @ 80 % / 15 s / 3 | breaker per rail |
| `…rail.bulkhead.*` | 16 payment, 2 inquiry per rail | semaphores |
| `payflow.traffic.*` | 20/s per subject; 10/min ops; admission 60 s; Retry-After 30 s | edge |
| `payflow.messaging.outbox.pipelined` / `drain-budget` | true / 1 s | relay mode |
| `payflow.saga.recovery-hold-age` | 15 s | hold recovery while commands are unpublished |
| `payflow.saga.rail-idempotency-window` | 24 h | manual-review RESUME guard |
| `payflow.reconciliation.interval-ms` / `grace` | 300000 / 2 m | reconciliation |
| `spring.lifecycle.timeout-per-shutdown-phase` | 25 s | graceful shutdown |
| `spring.task.scheduling.shutdown.await-termination-period` | 15 s | let a relay batch finish |
| profile `kafka-sasl` | – | SCRAM client config from `PAYFLOW_KAFKA_USERNAME/PASSWORD` |

## 5. Tests added

| Test | Proves |
|---|---|
| `SettlementRailResilienceTest` (real HTTP to the simulator, no Docker) | timeout → UNKNOWN and not retried; 503 retried exactly once; unreachable → NOT_SENT; breaker CLOSED → OPEN → fail fast → HALF_OPEN → CLOSED; slow calls open the breaker; per-rail isolation; bulkhead cap and NOT_SENT; void blocks late instructions but cannot undo an accepted one |
| `PaymentSagaServiceTest.recoveryHoldsWhileCommandsAreNotBeingPublished` | the metastable-loop fix |
| `ManualReviewIT` | both resolution paths end to end, idempotent replay, single audit row, scope enforcement |
| `ReconciliationIT` | drift detected, classified, confirmed on the 2nd run, never repaired, resolved after the correction |
| `TrafficControlIT` | per-subject 429 with Retry-After; admission 503 while Kafka is really paused; reopens after drain |
| `ArchitectureTest` (+3 rules) | Resilience4j only at outbound/edge boundaries; no telemetry SDKs in domain/application/contracts; inbound adapters never use outbound ports |
