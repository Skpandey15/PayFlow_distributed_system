# Load-test report: 20260927-161857-peak-final

Window: 2026-09-27T10:48:59.669000+00:00 to 2026-09-27T10:57:59.724000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 24249 |
| iterations_per_s | 44.79954574650362 |
| dropped_iterations | 50 |
| payments_accepted | 24239 |
| payments_throttled | 1 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.9999723939929329 |
| post_p50_ms | 14.297504 |
| post_p95_ms | 70.4089575 |
| post_p99_ms | 348.88445513000323 |
| post_max_ms | 2001.167476 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 45.1103 |
| api_5xx_ratio | 0.0 |
| api_post_p50_ms | 13.2 |
| api_post_p95_ms | 69.4 |
| api_post_p99_ms | 352.9 |
| api_get_p99_ms | 18.4 |
| saga_completed | 24281.5246 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 24281.5246} |
| saga_p50_ms | 3631.6 |
| saga_p95_ms | 38186.5 |
| saga_p99_ms | 44102.2 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 12457.5, "step=AWAITING_FUNDS": 12863.8, "step=AWAITING_RISK": 15286.7, "step=AWAITING_SETTLEMENT": 12455.1} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 105.0, "step=AWAITING_FUNDS": 280.0, "step=AWAITING_RISK": 151.0, "step=AWAITING_SETTLEMENT": 359.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 478.0, "step=AWAITING_FUNDS": 559.0, "step=AWAITING_RISK": 657.0, "step=AWAITING_SETTLEMENT": 548.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 180.1 |
| outbox_publish_delay_p99_ms | 2825.6 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 394.3, "outbox=payment.outbox_event": 3765.4, "outbox=settlement.outbox_event": 457.0} |
| outbox_send_p99_ms | 26.6 |
| outbox_published_per_s | 490.3514 |
| outbox_backlog_max | {"outbox=account.outbox_event": 5.0, "outbox=payment.outbox_event": 53.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 903.0, "group=payment-service,topic=fraud.events": 651.0, "group=payment-service,topic=settlement.events": 522.0, "group=account-service,topic=funds.commands": 284.0, "group=settlement-service,topic=settlement.commands": 275.0} |
| consumer_p99_ms | {"consumer=ledger-service": 44.6, "consumer=payment-service": 54.5, "consumer=account-service": 59.9, "consumer=fraud-service": 38.9, "consumer=settlement-service": 88.8} |
| events_consumed_per_s | 444.9888 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 38.0 |
| hikari_acquire_max_ms | 1649.3 |
| hikari_acquire_avg_ms | 0.3 |
| hikari_usage_avg_ms | 9.0 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1231.4093 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 24.0 |
| mongo_cmd_max_ms | 171.1 |
| mongo_cmd_avg_ms | 1.2 |
| jvm_heap_used_max_mb | 221 |
| jvm_heap_after_gc_max_mb | 82 |
| jvm_heap_committed_max_mb | 237 |
| gc_pause_max_ms | 38.0 |
| gc_pause_total_ms | 2435.3 |
| gc_count | 730.2789 |
| alloc_rate_mb_s | 185.5 |
| threads_max | 75.0 |
| process_cpu_avg | 0.6266 |
| process_cpu_max | 0.954 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 1.23 | 181 |
| payflow-kafka-1 | 28.9 | 93.09 | 813 |
| payflow-keycloak-1 | 0.5 | 1.51 | 4079 |
| payflow-mongo-1 | 19.3 | 52.93 | 397 |
| payflow-payflow-1 | 86.1 | 99.18 | 604 |
| payflow-postgres-1 | 47.8 | 55.85 | 564 |
| payflow-postgres-exporter-1 | 0.4 | 1.46 | 14 |
| payflow-prometheus-1 | 0.6 | 1.23 | 88 |
| payflow-settlement-rail-1 | 1.0 | 1.88 | 149 |
| payflow-tempo-1 | 0.9 | 2.55 | 87 |
