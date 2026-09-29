# Observability architecture (WP-03)

Decision record: ADR-020. This document is the operating manual: which signal answers which question, and how
one payment is followed across the asynchronous boundaries.

## 1. Three signals, three questions

| Signal | Question | Where | Retention (lab) |
|---|---|---|---|
| Metrics (Micrometer → Prometheus) | **What** is wrong, since when, how much? | Grafana dashboards, alert rules | 7 days |
| Traces (OpenTelemetry → Tempo) | **Where** did the time go, or which hop failed? | Grafana → Tempo | 24 h, 10 % head-sampled |
| Logs (ECS JSON, stdout) | **What happened** to this payment, and why? | `docker logs` / kubectl / (future) Loki | container lifetime |

They are joined by identifiers, not by timestamps:

| Id | Scope | Carried in |
|---|---|---|
| `traceId` / `spanId` | one technical trace (HTTP request, or a consumer delivery continuing it) | logs, spans, `traceparent` header on every Kafka record (stored in the outbox row) |
| `correlationId` | the business request (constant for the whole saga) | logs, envelopes, problem responses, manual-review audit |
| `eventId` / `causationId` | one message / the message that caused this one | logs, envelopes |
| `sagaId`, `aggregateId` (= paymentId) | the workflow / the payment | logs, envelopes |

## 2. Metrics catalogue (the ones alerts and dashboards rely on)

| Metric | Type | Labels | Answers |
|---|---|---|---|
| `http_server_requests_seconds` | histogram (+ SLO buckets 0.1/0.3/1 s) | uri, method, status | acceptance latency and availability |
| `payflow_saga_completion_seconds` | histogram (+ 5/10/30/60 s) | outcome | acceptance → terminal (completion SLI) |
| `payflow_saga_step_duration_seconds` | histogram | step | which participant is slow |
| `payflow_saga_open`, `payflow_saga_oldest_age_seconds` | gauge (DB count) | step | stuck payments, manual-review backlog |
| `payflow_saga_recovery_total`, `payflow_saga_recovery_held_total` | counter | action, step | recovery intervention and holding |
| `payflow_outbox_backlog`, `payflow_outbox_oldest_age_seconds` | gauge | outbox | publication health (admission control input) |
| `payflow_outbox_publish_delay_seconds` | histogram (+ 1/5/15 s) | outbox | commit → broker ack (publication SLI) |
| `payflow_outbox_send_latency_seconds`, `payflow_outbox_published_total`, `payflow_outbox_publish_failures_total` | histogram / counters | outbox | broker health seen by the relay |
| `payflow_kafka_consumer_lag_records` | gauge (broker-side) | group, topic | consumer falling behind (even if dead) |
| `payflow_kafka_lag_monitor_last_success_seconds` | gauge | none | is the lag number current? |
| `payflow_events_consumed_total`, `payflow_events_processing_seconds` | counter / histogram | topic, consumer, outcome | consumer throughput and latency |
| `payflow_events_failed_total`, `payflow_events_dead_lettered_total` | counters | topic, consumer, category | retry and DLT rates by failure class |
| `payflow_settlement_rail_calls_total` | counter | rail, operation, outcome | how the rail answers |
| `resilience4j_circuitbreaker_*`, `resilience4j_retry_calls_total`, `resilience4j_bulkhead_available_concurrent_calls` | Resilience4j | name, state, kind | breaker, retry, bulkhead |
| `payflow_traffic_rejected_total` | counter | policy | rate-limit and admission shedding |
| `payflow_manual_review_decisions_total` | counter | decision | operator activity |
| `payflow_reconciliation_*` | gauges / counters / timer | check, severity, confirmed, currency, result | financial drift |
| `hikaricp_*`, `jdbc_*` | pool, JDBC | pool | connection pool saturation and wait |
| `jvm_*`, `process_cpu_usage` | JVM | area, id, gc | heap, GC, threads, CPU |
| `pg_*` (postgres-exporter) | database | datname, mode, state | commits, locks, deadlocks, backends |

**Cardinality rule:** no ids as labels. The highest-cardinality custom metric is the lag gauge (5 groups × about
20 topics including retry and DLT).

## 3. Tracing one asynchronous payment

```
POST /api/v1/payments ──span: http.server──▶ CreatePaymentService ──jdbc spans──▶ COMMIT
      │ outbox rows store traceparent = this span
      ▼
OutboxRelay (scheduler; no parent) ── record header traceparent ─▶ Kafka
      ▼
fraud.commands consumer ──span: kafka.receive (child of the HTTP span)──▶ jdbc/mongo spans ──▶ RiskAssessed
      ▼   (traceparent propagated again through the outbox / direct send)
payment saga consumer ──span──▶ ReserveFunds ──▶ account consumer ──▶ ... ──▶ CaptureFunds ──▶ SETTLED
```

The whole saga is **one trace**: every consumer span is a child of the HTTP request span, via the traceparent
stored in the outbox row. The relay's own work (polling, batching) is a separate short trace; the time between
the HTTP span ending and the first consumer span starting **is** the outbox publication delay. That gap is also
measured directly by `payflow_outbox_publish_delay_seconds`.

**Operator procedure for one slow payment:**
1. Get the payment's `correlationId` (support ticket or problem response) or its id.
2. Search the logs for `correlationId=` to get the `traceId`.
3. Open the trace in Grafana → Explore → Tempo (sampled at 10 %). If it was not sampled, the logs still show
   every hop with its timing (`durationMs`) and the step metrics show the population's behaviour.
4. Compare with the "Why are payments taking longer?" panel: step p95, outbox age and consumer lag on one axis.

## 4. Dashboards

Generated by `deploy/observability/grafana/generate_dashboards.py`, provisioned read-only:

| Dashboard | Operational question |
|---|---|
| PayFlow overview | Are we taking payments, completing them on time, and is anything needing a human or misstated? |
| Payment latency | Acceptance vs completion percentiles; which step, which rail call |
| Kafka health | Lag (broker-side), throughput, failures by class, DLT, retry-topic lag |
| Outbox health | Oldest age (SLI + admission input), backlog, publish rate vs production, broker ack wait, shedding |
| Saga health | Open per step, oldest per step, recovery actions and holds, compensations, operator decisions |
| Database health | Pool active/max/pending, acquire wait, transaction length, commits/rollbacks, locks/deadlocks, backends, MongoDB latency |
| JVM health | Heap used/committed/max, old gen after GC (leak signal), GC pauses, allocation rate, CPU, threads |
| Resilience | Breaker state, rail outcomes, failure/slow rate, retries, bulkhead permits, edge shedding |
| Reconciliation | Confirmed CRITICAL drift, oldest mismatch, checks violated, misstated amount per currency, run health |

## 5. Logging (WP-02 standard preserved, WP-03 additions)

WP-03 added only context fields and a few owner-logged events:

| Field / event | Set by | Logged by |
|---|---|---|
| `dependency`, `circuitBreakerState`, `deliveryOutcome` | rail adapter (logging context) | the consumer boundary's single WARN |
| circuit state change | rail configuration | `payflow.resilience` (WARN on OPEN, else INFO) |
| admission closed / reopened | edge interceptor | `payflow.traffic` (transition only) |
| saga recovery held / resumed | recovery job | `payflow.saga.recovery` (transition only) |
| manual-review decision (`operator`, `decision`, `resumedStep`, `decisionId`) | ops controller | `payflow.ops.audit` |
| reconciliation findings summary | reconciliation job | `payflow.reconciliation` (WARN, only when findings exist) |

**Never logged:** tokens, passwords and secrets (Kafka SCRAM passwords, client secrets), account balances, fraud
evidence, request bodies, JDBC parameter values (disabled in datasource-micrometer).
