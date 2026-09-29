# Load-test report: 20260929-110031-degraded-fault-F01-kafka-unavailable

Window: 2026-09-29T05:30:37.286000+00:00 to 2026-09-29T05:33:37.312000+00:00 (+62s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3601 |
| iterations_per_s | 19.538688293234557 |
| dropped_iterations | 0 |
| payments_accepted | 2594 |
| payments_throttled | 1007 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.7926276771004942 |
| post_p50_ms | 14.505052 |
| post_p95_ms | 228.057238 |
| post_p99_ms | 424.426161 |
| post_max_ms | 728.15139 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 14.2571 |
| api_5xx_ratio | 0.2898 |
| api_post_p50_ms | 13.1 |
| api_post_p95_ms | 121.6 |
| api_post_p99_ms | 198.3 |
| api_get_p99_ms | 103.9 |
| saga_completed | 2644.3017 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 2644.3017} |
| saga_p50_ms | 107010.2 |
| saga_p95_ms | 148695.9 |
| saga_p99_ms | 158015.5 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 129410.2, "step=AWAITING_FUNDS": 127536.6, "step=AWAITING_RISK": 132361.0, "step=AWAITING_SETTLEMENT": 130910.5} |
| drain_seconds_after_load | 62 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 97.0, "step=AWAITING_FUNDS": 89.0, "step=AWAITING_RISK": 1291.0, "step=AWAITING_SETTLEMENT": 102.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 346.0, "step=AWAITING_FUNDS": 538.0, "step=AWAITING_RISK": 1301.0, "step=AWAITING_SETTLEMENT": 782.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 208.4 |
| outbox_publish_delay_p99_ms | 106003.6 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 1056.9, "outbox=payment.outbox_event": 108264.9, "outbox=settlement.outbox_event": 1110.7} |
| outbox_send_p99_ms | 4670.1 |
| outbox_published_per_s | 86.3751 |
| outbox_backlog_max | {"outbox=account.outbox_event": 6.0, "outbox=payment.outbox_event": 2453.0, "outbox=settlement.outbox_event": 4.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 85.0, "outbox=payment.outbox_event": 90.0, "outbox=settlement.outbox_event": 95.0} |
| consumer_lag_max_top5 | {"group=fraud-service,topic=fraud.commands": 954.0, "group=payment-service,topic=fraud.events": 731.0, "group=payment-service,topic=funds.events": 594.0, "group=payment-service,topic=settlement.events": 531.0, "group=settlement-service,topic=settlement.commands": 244.0} |
| consumer_p99_ms | {"consumer=ledger-service": 80.2, "consumer=payment-service": 98.1, "consumer=account-service": 113.3, "consumer=fraud-service": 336.3, "consumer=settlement-service": 372.8} |
| events_consumed_per_s | 69.3582 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 9.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 116.0 |
| hikari_acquire_avg_ms | 0.1 |
| hikari_usage_avg_ms | 17.1 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 250.9371 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 186.1 |
| mongo_cmd_avg_ms | 4.0 |
| jvm_heap_used_max_mb | 213 |
| jvm_heap_after_gc_max_mb | 76 |
| jvm_heap_committed_max_mb | 234 |
| gc_pause_max_ms | 67.0 |
| gc_pause_total_ms | 923.9 |
| gc_count | 104.0447 |
| alloc_rate_mb_s | 41.6 |
| threads_max | 72.0 |
| process_cpu_avg | 0.4878 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 1.63 | 323 |
| payflow-kafka-1 | 120.5 | 208.16 | 751 |
| payflow-keycloak-1 | 0.3 | 1.33 | 766 |
| payflow-mongo-1 | 14.5 | 59.6 | 394 |
| payflow-payflow-1 | 95.8 | 203.75 | 582 |
| payflow-postgres-1 | 18.6 | 48.41 | 672 |
| payflow-postgres-exporter-1 | 0.7 | 2.54 | 9 |
| payflow-prometheus-1 | 0.6 | 1.05 | 68 |
| payflow-settlement-rail-1 | 0.4 | 1.63 | 45 |
| payflow-tempo-1 | 0.5 | 1.32 | 118 |
