# ADR-020: Observability stack: Micrometer + Prometheus (pull, authenticated), OpenTelemetry traces to Tempo, structured logs

- Status: Accepted (WP-03)
- Date: 2026-09-27

## Context

WP-02 propagated W3C trace context and logged structured JSON, but exported nothing: no metrics backend, no trace
backend and no dashboards. WP-03 needs:

| Need | Required for |
|---|---|
| Latency distributions (p50/p95/p99) aggregated across replicas | Performance claims |
| Asynchronous traces across the outbox → Kafka → consumer hops | Following one payment |
| SLI time series | Burn-rate alerting |

## Decision

| Signal | Answers | Implementation |
|---|---|---|
| Metrics | *What* is wrong, how much, since when | Micrometer. The Prometheus endpoint `/actuator/prometheus` requires scope `ops:metrics`; Prometheus obtains a client-credentials token (`payflow-monitoring` client) with its `oauth2` scrape config. Percentile **histograms** (not client-side percentiles) only for the latency SLIs, plus exact SLO buckets (0.3 s, 30 s, 5 s). |
| Traces | *Where* the time or failure is | OpenTelemetry via Micrometer Tracing, OTLP/HTTP to Tempo. Head sampling 10 % in load tests and production (1.0 in dev). JDBC spans via datasource-micrometer (connection acquisition + statements, **no parameter values**). Kafka consumer spans continue the `traceparent` stored in the outbox row, so one payment's trace spans HTTP → outbox → broker → consumers → saga. |
| Logs | *What happened* to this payment | Unchanged WP-02 ECS JSON with traceId, spanId, correlationId, causationId, eventId, sagaId, topic, partition, offset and retryAttempt. WP-03 adds dependency, circuitBreakerState, deliveryOutcome, operator and decision. No new logging framework. |
| Dashboards | Operational questions | Grafana, provisioned from `deploy/observability/grafana/generate_dashboards.py`. Every panel title is a question. |
| Alerts | Symptoms that need a human | Prometheus rules (`payflow-slo.yml`, `payflow-alerts.yml`), multi-window burn rates. |

Custom metrics are designed for bounded cardinality. There are no payment, account or trace ids as labels;
labels are step, outbox, group/topic, rail, outcome, check, severity and currency.

Two measurements are taken **broker-side / database-side** on purpose:
- **Consumer lag** (`KafkaLagMonitor`: log-end minus committed offset). The client metric disappears exactly when
  a consumer dies.
- **Open sagas per step** (a DB count).

## Alternatives

| Alternative | Why not |
|---|---|
| OTLP metrics push to a collector | Equally valid. Pull keeps the lab smaller (no collector) and makes "target down" itself a signal. |
| Unauthenticated metrics on a separate management port | Common, but metrics reveal traffic shape and error codes. The Zero Trust rule is "every caller authenticates". |
| Loki for logs | Useful (trace → logs in one click) but another stateful component. JSON logs with traceId are searchable with `docker logs` / kubectl in the lab; Loki is future work. |
| Tail sampling (collector) | Better at keeping errors and slow traces. Needs a collector tier; deferred with the collector. |
| 100 % sampling | The export volume at load was not justified. The trace context is propagated for 100 % of requests regardless, so logs still carry traceIds. |

## Trade-offs

- **Trace export and JDBC spans cost CPU.** The baseline and all comparisons were measured with this exact
  configuration, so the cost is inside the numbers.
- **Head sampling can miss rare failures.** Metrics and logs cover every event; traces are a sample.
- **The scrape token expires every 5 minutes.** Prometheus refreshes it; a broken IdP shows as `PayFlowTargetDown`.

## Operational consequences

- New components in compose: Prometheus, Grafana, Tempo and postgres-exporter. They are resource-limited and
  share the lab host with the system under test (noted in every performance report).
- Kubernetes: scrape from the `observability` namespace (NetworkPolicy), OTLP to `otel-collector.observability`.

## Failure implications

| Failure | Behaviour |
|---|---|
| Tempo down | Spans are dropped by the exporter (bounded queue); the application is unaffected. |
| Prometheus down | No alerts, which is the monitoring system's own single point of failure. Production: HA pair plus external watchdog (future). |
| Keycloak down | Scrapes fail after the token expires; `PayFlowTargetDown` fires (it is not a PayFlow outage). This is documented in the runbook. |
