# Load-test report: 20260927-115130-smoke-warmup-baseline

Window: 2026-09-27T06:21:34.783000+00:00 to 2026-09-27T06:23:04.797000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 450 |
| iterations_per_s | 4.794100679520446 |
| dropped_iterations | 0 |
| payments_accepted | 450 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 12.4201175 |
| post_p95_ms | 24.561262049999982 |
| post_p99_ms | 33.65055485999996 |
| post_max_ms | 98.829571 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 11.9 |
| api_post_p95_ms | 23.6 |
| api_post_p99_ms | 33.2 |
| api_get_p99_ms | 8.2 |
| saga_completed | 453.6438 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 453.6438} |
| saga_p50_ms | 1683.7 |
| saga_p95_ms | 2113.7 |
| saga_p99_ms | 2326.3 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 665.1, "step=AWAITING_FUNDS": 725.3, "step=AWAITING_RISK": 440.7, "step=AWAITING_SETTLEMENT": 588.9} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 4.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 1.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 4.0, "step=AWAITING_FUNDS": 4.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 257.9 |
| outbox_publish_delay_p99_ms | 428.8 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 356.3, "outbox=payment.outbox_event": 433.0, "outbox=settlement.outbox_event": 433.1} |
| outbox_send_p99_ms | 11.5 |
| outbox_published_per_s | 54.528 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 9.0, "outbox=settlement.outbox_event": 0.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 1.0, "group=account-service,topic=funds.commands": 0.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=ledger-service": 13.9, "consumer=payment-service": 19.0, "consumer=account-service": 19.6, "consumer=fraud-service": 21.8, "consumer=settlement-service": 17.2} |
| events_consumed_per_s | 49.6697 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 3.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 5.3 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 8.2 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 154.0471 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 60.5 |
| mongo_cmd_avg_ms | 0.7 |
| jvm_heap_used_max_mb | 144 |
| jvm_heap_after_gc_max_mb | 90 |
| jvm_heap_committed_max_mb | 213 |
| gc_pause_max_ms | 182.0 |
| gc_pause_total_ms | 273.6 |
| gc_count | 55.7652 |
| alloc_rate_mb_s | 32.4 |
| threads_max | 188.0 |
| process_cpu_avg | 0.3413 |
| process_cpu_max | 0.564 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.21 | 320 |
| payflow-kafka-1 | 56.5 | 182.23 | 568 |
| payflow-keycloak-1 | 0.4 | 1.8 | 675 |
| payflow-mongo-1 | 8.3 | 47.06 | 269 |
| payflow-payflow-1 | 64.9 | 111.66 | 518 |
| payflow-postgres-1 | 10.8 | 17.78 | 102 |
| payflow-postgres-exporter-1 | 0.6 | 3.15 | 8 |
| payflow-prometheus-1 | 0.5 | 1.08 | 110 |
| payflow-tempo-1 | 0.6 | 1.79 | 113 |
