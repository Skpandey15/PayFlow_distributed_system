# Load-test report: 20260929-142251-degraded-fault-F05-mongodb-unavailable

Window: 2026-09-29T08:52:54.584000+00:00 to 2026-09-29T08:55:54.618000+00:00 (+93s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3601 |
| iterations_per_s | 19.846817708448707 |
| dropped_iterations | 0 |
| payments_accepted | 3601 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 15.096078 |
| post_p95_ms | 29.090129 |
| post_p99_ms | 47.932685 |
| post_max_ms | 95.287337 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 20.0 |
| api_5xx_ratio | 0.0 |
| api_post_p50_ms | 14.3 |
| api_post_p95_ms | 27.4 |
| api_post_p99_ms | 45.7 |
| api_get_p99_ms | 7.7 |
| saga_completed | 3641.0111 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3641.0111} |
| saga_p50_ms | 91161.0 |
| saga_p95_ms | 162935.1 |
| saga_p99_ms | 179188.6 |
| saga_step_p99_ms | {"step=AWAITING_SETTLEMENT": 7724.7, "step=AWAITING_CAPTURE": 10332.5, "step=AWAITING_FUNDS": 9455.1, "step=AWAITING_RISK": 135372.8} |
| drain_seconds_after_load | 93 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 2140.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 286.0, "step=AWAITING_FUNDS": 675.0, "step=AWAITING_RISK": 2456.0, "step=AWAITING_SETTLEMENT": 372.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 166.2 |
| outbox_publish_delay_p99_ms | 340.4 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=payment.outbox_event": 341.2, "outbox=settlement.outbox_event": 349.3, "outbox=account.outbox_event": 311.6} |
| outbox_send_p99_ms | 24.1 |
| outbox_published_per_s | 101.2743 |
| outbox_backlog_max | {"outbox=account.outbox_event": 2.0, "outbox=payment.outbox_event": 17.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=fraud-service,topic=fraud.commands": 2832.0, "group=payment-service,topic=funds.events": 1208.0, "group=payment-service,topic=fraud.events": 1047.0, "group=payment-service,topic=settlement.events": 583.0, "group=account-service,topic=funds.commands": 140.0} |
| consumer_p99_ms | {"consumer=payment-service": 42.5, "consumer=account-service": 48.6, "consumer=fraud-service": 58.0, "consumer=settlement-service": 67.8, "consumer=ledger-service": 32.9} |
| events_consumed_per_s | 65.4057 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 834.9115, "category=CONCURRENCY": 1.0111} |
| events_dead_lettered | 175.5119 |
| hikari_active_max | 8.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 30.6 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 5.7 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 318.1943 |
| pg_rollbacks | 1.0302 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 61.9 |
| mongo_cmd_avg_ms | 1.4 |
| jvm_heap_used_max_mb | 322 |
| jvm_heap_after_gc_max_mb | 124 |
| jvm_heap_committed_max_mb | 364 |
| gc_pause_max_ms | 19.0 |
| gc_pause_total_ms | 424.7 |
| gc_count | 82.9111 |
| alloc_rate_mb_s | 45.4 |
| threads_max | 196.0 |
| process_cpu_avg | 0.2951 |
| process_cpu_max | 0.505 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.3 | 2.19 | 338 |
| payflow-kafka-1 | 47.1 | 179.87 | 1227 |
| payflow-keycloak-1 | 0.3 | 0.32 | 758 |
| payflow-mongo-1 | 12.8 | 27.14 | 341 |
| payflow-payflow-1 | 52.9 | 105.62 | 741 |
| payflow-postgres-1 | 20.5 | 47.49 | 435 |
| payflow-postgres-exporter-1 | 0.3 | 2.15 | 10 |
| payflow-prometheus-1 | 0.7 | 2.91 | 83 |
| payflow-settlement-rail-1 | 0.3 | 1.13 | 47 |
| payflow-tempo-1 | 0.8 | 3.54 | 149 |
