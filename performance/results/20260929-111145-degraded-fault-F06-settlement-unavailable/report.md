# Load-test report: 20260929-111145-degraded-fault-F06-settlement-unavailable

Window: 2026-09-29T05:41:47.746000+00:00 to 2026-09-29T05:44:47.751000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3600 |
| iterations_per_s | 19.856675567605258 |
| dropped_iterations | 0 |
| payments_accepted | 3600 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 11.58671 |
| post_p95_ms | 25.888138699999995 |
| post_p99_ms | 34.33149536999998 |
| post_max_ms | 310.713771 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 20.0 |
| api_5xx_ratio | 0.0 |
| api_post_p50_ms | 10.9 |
| api_post_p95_ms | 24.1 |
| api_post_p99_ms | 32.6 |
| api_get_p99_ms | 8.1 |
| saga_completed | 3694.0439 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3694.0439} |
| saga_p50_ms | 1612.1 |
| saga_p95_ms | 1775.2 |
| saga_p99_ms | 1800.3 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 536.2, "step=AWAITING_FUNDS": 536.1, "step=AWAITING_RISK": 352.3, "step=AWAITING_SETTLEMENT": 624.6} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 7.0, "step=AWAITING_FUNDS": 6.0, "step=AWAITING_RISK": 6.0, "step=AWAITING_SETTLEMENT": 12.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 10.0, "step=AWAITING_FUNDS": 11.0, "step=AWAITING_RISK": 6.0, "step=AWAITING_SETTLEMENT": 12.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 216.3 |
| outbox_publish_delay_p99_ms | 268.3 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 236.9, "outbox=payment.outbox_event": 284.9, "outbox=settlement.outbox_event": 268.1} |
| outbox_send_p99_ms | 18.9 |
| outbox_published_per_s | 219.9029 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 12.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 7.0, "group=payment-service,topic=settlement.events": 5.0, "group=payment-service,topic=fraud.events": 4.0, "group=settlement-service,topic=settlement.commands": 3.0, "group=fraud-service,topic=fraud.commands": 3.0} |
| consumer_p99_ms | {"consumer=ledger-service": 20.1, "consumer=payment-service": 23.7, "consumer=account-service": 27.3, "consumer=fraud-service": 27.9, "consumer=settlement-service": 33.0} |
| events_consumed_per_s | 199.9143 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 0.0, "category=UNKNOWN": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 9.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 4.6 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 5.6 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 562.7771 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 37.3 |
| mongo_cmd_avg_ms | 1.4 |
| jvm_heap_used_max_mb | 215 |
| jvm_heap_after_gc_max_mb | 80 |
| jvm_heap_committed_max_mb | 234 |
| gc_pause_max_ms | 10.0 |
| gc_pause_total_ms | 441.6 |
| gc_count | 118.3659 |
| alloc_rate_mb_s | 87.9 |
| threads_max | 73.0 |
| process_cpu_avg | 0.3902 |
| process_cpu_max | 0.481 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 1.86 | 325 |
| payflow-kafka-1 | 42.9 | 171.57 | 633 |
| payflow-keycloak-1 | 0.2 | 0.25 | 771 |
| payflow-mongo-1 | 13.4 | 46.48 | 404 |
| payflow-payflow-1 | 74.2 | 108.19 | 592 |
| payflow-postgres-1 | 37.8 | 74.77 | 709 |
| payflow-postgres-exporter-1 | 0.2 | 1.72 | 10 |
| payflow-prometheus-1 | 0.6 | 1.73 | 73 |
| payflow-settlement-rail-1 | 0.7 | 0.91 | 44 |
| payflow-tempo-1 | 1.1 | 6.16 | 178 |
