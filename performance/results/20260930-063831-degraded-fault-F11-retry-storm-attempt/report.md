# Load-test report: 20260930-063831-degraded-fault-F11-retry-storm-attempt

Window: 2026-09-30T06:38:53.697000+00:00 to 2026-09-30T06:43:06.549000+00:00 (+177s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 4798 |
| iterations_per_s | 18.67794793890698 |
| dropped_iterations | 3 |
| payments_accepted | 4798 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 11.6584055 |
| post_p95_ms | 163.4066378999998 |
| post_p99_ms | 375.76355114999933 |
| post_max_ms | 1990.686426 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 18.9294 |
| api_5xx_ratio | None |
| api_post_p50_ms | 10.7 |
| api_post_p95_ms | 103.1 |
| api_post_p99_ms | 239.3 |
| api_get_p99_ms | 61.6 |
| saga_completed | 4856.071 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 4856.071} |
| saga_p50_ms | 95421.1 |
| saga_p95_ms | 266766.2 |
| saga_p99_ms | 346152.6 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 6163.9, "step=AWAITING_FUNDS": 6154.5, "step=AWAITING_SETTLEMENT": 196421.4, "step=AWAITING_RISK": 6067.8} |
| drain_seconds_after_load | 177 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 17.0, "step=AWAITING_FUNDS": 11.0, "step=AWAITING_RISK": 8.0, "step=AWAITING_SETTLEMENT": 1690.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 78.0, "step=AWAITING_FUNDS": 80.0, "step=AWAITING_RISK": 75.0, "step=AWAITING_SETTLEMENT": 2723.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 207.9 |
| outbox_publish_delay_p99_ms | 1013.2 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 898.4, "outbox=payment.outbox_event": 1044.5, "outbox=settlement.outbox_event": 975.4} |
| outbox_send_p99_ms | 82.7 |
| outbox_published_per_s | 183.4895 |
| outbox_backlog_max | {"outbox=account.outbox_event": 7.0, "outbox=payment.outbox_event": 22.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 138.0, "group=payment-service,topic=settlement.events": 81.0, "group=payment-service,topic=fraud.events": 75.0, "group=settlement-service,topic=settlement.commands": 39.0, "group=account-service,topic=funds.commands": 28.0} |
| consumer_p99_ms | {"consumer=fraud-service": 142.3, "consumer=ledger-service": 66.8, "consumer=payment-service": 84.1, "consumer=account-service": 99.5, "consumer=settlement-service": 177.5} |
| events_consumed_per_s | 164.4141 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 40.8105, "category=CONCURRENCY": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 18.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 646.1 |
| hikari_acquire_avg_ms | 0.1 |
| hikari_usage_avg_ms | 8.6 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 511.92 |
| pg_rollbacks | 1.0125 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 83.7 |
| mongo_cmd_avg_ms | 2.2 |
| jvm_heap_used_max_mb | 262 |
| jvm_heap_after_gc_max_mb | 97 |
| jvm_heap_committed_max_mb | 286 |
| gc_pause_max_ms | 60.0 |
| gc_pause_total_ms | 1520.9 |
| gc_count | 170.8459 |
| alloc_rate_mb_s | 78.3 |
| threads_max | 195.0 |
| process_cpu_avg | 0.5212 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.47 | 182 |
| payflow-kafka-1 | 51.2 | 205.01 | 507 |
| payflow-keycloak-1 | 1.1 | 16.86 | 540 |
| payflow-mongo-1 | 11.2 | 54.13 | 260 |
| payflow-payflow-1 | 106.4 | 210.72 | 646 |
| payflow-postgres-1 | 29.0 | 48.25 | 161 |
| payflow-postgres-exporter-1 | 0.3 | 2.03 | 13 |
| payflow-prometheus-1 | 0.7 | 2.71 | 90 |
| payflow-settlement-rail-1 | 1.0 | 4.42 | 37 |
| payflow-tempo-1 | 1.1 | 4.44 | 976 |
