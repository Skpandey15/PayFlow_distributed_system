# Load-test report: 20260927-190152-peak-serialgc-fresh

Window: 2026-09-27T13:31:55.183000+00:00 to 2026-09-27T13:40:55.267000+00:00 (+30s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 24300 |
| iterations_per_s | 44.857307218604454 |
| dropped_iterations | 0 |
| payments_accepted | 24300 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 13.1605435 |
| post_p95_ms | 51.511300750000004 |
| post_p99_ms | 176.04622593999972 |
| post_max_ms | 781.572006 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 45.228 |
| api_5xx_ratio | None |
| api_post_p50_ms | 11.7 |
| api_post_p95_ms | 46.8 |
| api_post_p99_ms | 172.7 |
| api_get_p99_ms | 13.4 |
| saga_completed | 24456.531 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 24456.531} |
| saga_p50_ms | 1864.4 |
| saga_p95_ms | 13873.6 |
| saga_p99_ms | 20323.8 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 6402.2, "step=AWAITING_FUNDS": 6238.0, "step=AWAITING_RISK": 4830.3, "step=AWAITING_SETTLEMENT": 5110.6} |
| drain_seconds_after_load | 30 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 14.0, "step=AWAITING_FUNDS": 11.0, "step=AWAITING_RISK": 3.0, "step=AWAITING_SETTLEMENT": 11.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 219.0, "step=AWAITING_FUNDS": 228.0, "step=AWAITING_RISK": 177.0, "step=AWAITING_SETTLEMENT": 183.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 184.8 |
| outbox_publish_delay_p99_ms | 355.5 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=payment.outbox_event": 355.0, "outbox=settlement.outbox_event": 406.9, "outbox=account.outbox_event": 347.6} |
| outbox_send_p99_ms | 33.5 |
| outbox_published_per_s | 497.5439 |
| outbox_backlog_max | {"outbox=account.outbox_event": 3.0, "outbox=payment.outbox_event": 36.0, "outbox=settlement.outbox_event": 4.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 485.0, "group=payment-service,topic=settlement.events": 169.0, "group=payment-service,topic=fraud.events": 144.0, "group=account-service,topic=funds.commands": 37.0, "group=fraud-service,topic=fraud.commands": 26.0} |
| consumer_p99_ms | {"consumer=ledger-service": 43.6, "consumer=payment-service": 45.1, "consumer=account-service": 50.2, "consumer=fraud-service": 39.3, "consumer=settlement-service": 80.2} |
| events_consumed_per_s | 452.3252 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 14.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 553.1 |
| hikari_acquire_avg_ms | 0.1 |
| hikari_usage_avg_ms | 7.8 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1245.6916 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 58.4 |
| mongo_cmd_avg_ms | 1.3 |
| jvm_heap_used_max_mb | 145 |
| jvm_heap_after_gc_max_mb | 96 |
| jvm_heap_committed_max_mb | 186 |
| gc_pause_max_ms | 221.0 |
| gc_pause_total_ms | 6698.8 |
| gc_count | 1986.4248 |
| alloc_rate_mb_s | 187.9 |
| threads_max | 73.0 |
| process_cpu_avg | 0.6308 |
| process_cpu_max | 0.868 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 3.8 | 9.09 | 311 |
| payflow-kafka-1 | 74.6 | 159.85 | 612 |
| payflow-keycloak-1 | 0.2 | 0.27 | 702 |
| payflow-mongo-1 | 5.6 | 5.89 | 221 |
| payflow-payflow-1 | 121.0 | 169.67 | 496 |
| payflow-postgres-1 | 43.9 | 51.12 | 148 |
| payflow-postgres-exporter-1 | 0.0 | 0.0 | 11 |
| payflow-prometheus-1 | 0.5 | 1.01 | 67 |
| payflow-settlement-rail-1 | 3.0 | 3.67 | 38 |
| payflow-tempo-1 | 1.2 | 1.25 | 112 |
