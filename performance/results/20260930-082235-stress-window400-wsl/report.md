# Load-test report: 20260930-082235-stress-window400-wsl

Window: 2026-09-30T08:22:38.556000+00:00 to 2026-09-30T08:33:03.840000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 113070 |
| iterations_per_s | 180.122895261567 |
| dropped_iterations | 29 |
| payments_accepted | 35043 |
| payments_throttled | 78027 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.4028424049470397 |
| post_p50_ms | 2.902837 |
| post_p95_ms | 49.97859434999986 |
| post_p99_ms | 105.54629902999999 |
| post_max_ms | 2719.246635 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 56.4059 |
| api_5xx_ratio | 0.6895 |
| api_post_p50_ms | 0.8 |
| api_post_p95_ms | 36.9 |
| api_post_p99_ms | 81.1 |
| api_get_p99_ms | 39.4 |
| saga_completed | 35301.2518 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 35301.2518} |
| saga_p50_ms | 6656.0 |
| saga_p95_ms | 15717.0 |
| saga_p99_ms | 21683.7 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 5406.3, "step=AWAITING_CAPTURE": 6880.2, "step=AWAITING_FUNDS": 6845.3, "step=AWAITING_SETTLEMENT": 5433.8} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 157.0, "step=AWAITING_FUNDS": 99.0, "step=AWAITING_RISK": 116.0, "step=AWAITING_SETTLEMENT": 87.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 174.0, "step=AWAITING_FUNDS": 212.0, "step=AWAITING_RISK": 194.0, "step=AWAITING_SETTLEMENT": 172.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 146.5 |
| outbox_publish_delay_p99_ms | 529.9 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=settlement.outbox_event": 571.5, "outbox=account.outbox_event": 563.5, "outbox=payment.outbox_event": 516.8} |
| outbox_send_p99_ms | 56.4 |
| outbox_published_per_s | 620.2314 |
| outbox_backlog_max | {"outbox=account.outbox_event": 5.0, "outbox=payment.outbox_event": 101.0, "outbox=settlement.outbox_event": 5.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 302.0, "group=payment-service,topic=fraud.events": 167.0, "group=payment-service,topic=settlement.events": 167.0, "group=account-service,topic=funds.commands": 66.0, "group=fraud-service,topic=fraud.commands": 42.0} |
| consumer_p99_ms | {"consumer=fraud-service": 73.9, "consumer=ledger-service": 46.7, "consumer=payment-service": 53.0, "consumer=account-service": 63.2, "consumer=settlement-service": 97.6} |
| events_consumed_per_s | 563.8545 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 17.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 210.3 |
| hikari_acquire_avg_ms | 0.1 |
| hikari_usage_avg_ms | 8.5 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1561.1715 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 275.6 |
| mongo_cmd_avg_ms | 1.7 |
| jvm_heap_used_max_mb | 245 |
| jvm_heap_after_gc_max_mb | 106 |
| jvm_heap_committed_max_mb | 264 |
| gc_pause_max_ms | 445.0 |
| gc_pause_total_ms | 5863.0 |
| gc_count | 1076.4543 |
| alloc_rate_mb_s | 250.9 |
| threads_max | 197.0 |
| process_cpu_avg | 0.8296 |
| process_cpu_max | 0.992 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 9.5 | 194 |
| payflow-kafka-1 | 66.1 | 194.48 | 501 |
| payflow-keycloak-1 | 3.4 | 84.83 | 564 |
| payflow-mongo-1 | 17.5 | 63.88 | 302 |
| payflow-payflow-1 | 168.9 | 211.51 | 631 |
| payflow-postgres-1 | 78.4 | 122.22 | 576 |
| payflow-postgres-exporter-1 | 0.5 | 3.98 | 13 |
| payflow-prometheus-1 | 0.6 | 4.23 | 84 |
| payflow-settlement-rail-1 | 2.3 | 12.88 | 38 |
| payflow-tempo-1 | 1.8 | 10.88 | 106 |
