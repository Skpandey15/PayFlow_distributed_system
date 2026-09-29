# Logging Standard

## Format

Logs are ECS JSON (`logging.structured.format.console: ecs`). The `local` and `test` profiles use plain text with key-values. Every field below is either an MDC entry (set by the boundary) or an SLF4J key-value.

## Fields

| Field | Set by | When |
|---|---|---|
| traceId, spanId | Micrometer Tracing (HTTP + Kafka listener observations) | always, when a span is active |
| correlationId | `CorrelationIdFilter` (HTTP), `EventConsumerSupport` (from the envelope), `SagaRecoveryJob` (from the saga row) | always |
| causationId | `EventConsumerSupport` (= the eventId being processed) | consumer work |
| eventId, eventType, eventVersion, aggregateId | `EventConsumerSupport` | consumer work |
| topic, partition, offset, consumerGroup, retryAttempt | `EventConsumerSupport` | consumer work |
| sagaId | envelope / `PaymentSagaListener` / `SagaRecoveryJob` | saga work |
| sagaStep, sagaStepFrom | `PaymentSagaListener`, `SagaRecoveryJob` | saga transitions |
| outcome (PROCESSED / DUPLICATE / STALE / IGNORED), durationMs | `EventConsumerSupport` | every consumed event |
| failureCategory, errorCode, retryable | `EventConsumerSupport`, `DeadLetterObserver` | failures |
| outbox, eventId, errorCode | `OutboxRelay` | publication failures |
| dependency, circuitBreakerState, deliveryOutcome | `HttpSettlementRailGateway` (logging context) | rail call failures (carried by the consumer's single WARN) |
| operator, decision, resumedStep, decisionId | `ManualReviewController` (`payflow.ops.audit`) | each manual-review decision |
| oldestUnpublishedAgeSeconds | `TrafficControlInterceptor` (`payflow.traffic`) | admission open/close transitions |

Real example (live, abridged):

```json
{"message":"saga advanced","traceId":"a49215e8…","correlationId":"14cb9fcd…","eventId":"01a0df0a-d1e8…",
 "causationId":"01a0df0a-d1e8…","sagaId":"01a0df0a-96ec…","eventType":"FundsCaptured","topic":"funds.events",
 "partition":"5","offset":"5","consumerGroup":"payment-service","retryAttempt":"1",
 "sagaStepFrom":"AWAITING_CAPTURE","sagaStep":"COMPLETED"}
```

## Log once, at the owning boundary

| Boundary | Owns | Level |
|---|---|---|
| HTTP (`ApiExceptionHandler`) | synchronous request failures | WARN (503), ERROR (500) |
| Kafka consumer (`EventConsumerSupport`) | each processing attempt's outcome or failure | INFO consumed; WARN failed (with category, retryable) |
| DLT (`DeadLetterObserver`) | final failure of a message | **ERROR** (one line; the alerting signal) |
| Saga (`PaymentSagaListener`) | saga transitions, compensation start, stale replies | INFO; WARN on compensation |
| Recovery (`SagaRecoveryJob`) | re-issue / compensation / escalation | WARN; **ERROR** on MANUAL_REVIEW |
| Outbox relay | publication failure (per batch) | WARN |
| Rail resilience (`payflow.resilience`) | circuit breaker **state changes** only | WARN on OPEN, INFO otherwise |
| Edge (`payflow.traffic`) | admission closed / reopened (transitions only; never per rejected request) | WARN / INFO |
| Recovery hold | held / resumed transitions | WARN / INFO |
| Ops audit (`payflow.ops.audit`) | manual-review decisions | INFO |
| Reconciliation (`payflow.reconciliation`) | runs with findings (summary), failed runs | WARN |

Lower layers throw or translate and never log errors themselves. Spring Kafka's own error-handler logging is lowered to DEBUG, and Hibernate's `SqlExceptionHelper` is off (WP-01), so one failure produces exactly one WARN per attempt and one ERROR when it is final.

## Never logged

- JWTs or Authorization headers
- passwords or connection strings with credentials
- full event payloads (only ids and types)
- fraud evidence (IP, device, user agent)
- account balances
- Kafka SCRAM passwords, client secrets, the monitoring scrape token
- JDBC parameter values (disabled in the datasource-micrometer span configuration)
- stack traces for classified failures (only for UNKNOWN, and only in internal logs, never in events)
