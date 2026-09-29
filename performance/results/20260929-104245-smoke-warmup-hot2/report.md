# Load-test report: 20260929-104245-smoke-warmup-hot2

Window: 2026-09-29T05:12:49.812000+00:00 to 2026-09-29T05:14:19.823000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.826473950554243 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 16.002452 |
| post_p95_ms | 29.715124000000003 |
| post_p99_ms | 43.997814 |
| post_max_ms | 62.03473 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0116 |
| api_5xx_ratio | None |
| api_post_p50_ms | 14.8 |
| api_post_p95_ms | 28.2 |
| api_post_p99_ms | 38.8 |
| api_get_p99_ms | 7.0 |
| saga_completed | 454.7626 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 454.7626} |
| saga_p50_ms | 1617.2 |
| saga_p95_ms | 1785.0 |
| saga_p99_ms | 2052.4 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 536.8, "step=AWAITING_FUNDS": 536.8, "step=AWAITING_RISK": 346.0, "step=AWAITING_SETTLEMENT": 646.6} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 2.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 228.1 |
| outbox_publish_delay_p99_ms | 332.7 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 244.3, "outbox=payment.outbox_event": 338.7, "outbox=settlement.outbox_event": 317.9} |
| outbox_send_p99_ms | 20.7 |
| outbox_published_per_s | 54.5322 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=settlement.events": 1.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=ledger-service": 20.2, "consumer=payment-service": 26.3, "consumer=account-service": 31.2, "consumer=fraud-service": 32.8, "consumer=settlement-service": 37.2} |
| events_consumed_per_s | 49.6326 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 4.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 4.1 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 6.9 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 157.8353 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 41.5 |
| mongo_cmd_avg_ms | 1.5 |
| jvm_heap_used_max_mb | 194 |
| jvm_heap_after_gc_max_mb | 75 |
| jvm_heap_committed_max_mb | 230 |
| gc_pause_max_ms | 61.0 |
| gc_pause_total_ms | 224.9 |
| gc_count | 18.15 |
| alloc_rate_mb_s | 25.3 |
| threads_max | 70.0 |
| process_cpu_avg | 0.3544 |
| process_cpu_max | 0.684 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 1.75 | 320 |
| payflow-kafka-1 | 51.3 | 175.25 | 554 |
| payflow-keycloak-1 | 0.2 | 0.24 | 749 |
| payflow-mongo-1 | 11.0 | 42.16 | 207 |
| payflow-payflow-1 | 67.4 | 103.16 | 574 |
| payflow-postgres-1 | 13.9 | 21.46 | 106 |
| payflow-postgres-exporter-1 | 0.3 | 2.23 | 9 |
| payflow-prometheus-1 | 0.5 | 0.7 | 50 |
| payflow-settlement-rail-1 | 1.4 | 2.04 | 36 |
| payflow-tempo-1 | 0.4 | 0.53 | 106 |
