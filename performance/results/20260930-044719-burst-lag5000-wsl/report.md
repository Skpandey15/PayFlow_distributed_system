# Load-test report: 20260930-044719-burst-lag5000-wsl

Window: 2026-09-30T04:47:21.848000+00:00 to 2026-09-30T04:54:01.876000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 16699 |
| iterations_per_s | 41.56050009861128 |
| dropped_iterations | 0 |
| payments_accepted | 16617 |
| payments_throttled | 80 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.9967901135497331 |
| post_p50_ms | 30.820786 |
| post_p95_ms | 210.26130419999987 |
| post_p99_ms | 387.75180700000016 |
| post_max_ms | 2163.819562 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 41.8208 |
| api_5xx_ratio | 0.0042 |
| api_post_p50_ms | 24.1 |
| api_post_p95_ms | 169.3 |
| api_post_p99_ms | 343.8 |
| api_get_p99_ms | 159.8 |
| saga_completed | 16809.7755 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 16809.7755} |
| saga_p50_ms | 69402.5 |
| saga_p95_ms | 106270.5 |
| saga_p99_ms | 112880.1 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 37434.4, "step=AWAITING_SETTLEMENT": 41348.1, "step=AWAITING_RISK": 55402.1, "step=AWAITING_CAPTURE": 34135.6} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 10.0, "step=AWAITING_FUNDS": 10.0, "step=AWAITING_RISK": 3.0, "step=AWAITING_SETTLEMENT": 10.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 1738.0, "step=AWAITING_FUNDS": 2101.0, "step=AWAITING_RISK": 3398.0, "step=AWAITING_SETTLEMENT": 2434.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 172.2 |
| outbox_publish_delay_p99_ms | 870.8 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 872.8, "outbox=payment.outbox_event": 885.8, "outbox=settlement.outbox_event": 744.8} |
| outbox_send_p99_ms | 79.5 |
| outbox_published_per_s | 461.2286 |
| outbox_backlog_max | {"outbox=account.outbox_event": 5.0, "outbox=payment.outbox_event": 165.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=fraud-service,topic=fraud.commands": 2895.0, "group=payment-service,topic=fraud.events": 2865.0, "group=payment-service,topic=funds.events": 2393.0, "group=payment-service,topic=settlement.events": 2323.0, "group=account-service,topic=funds.commands": 549.0} |
| consumer_p99_ms | {"consumer=fraud-service": 101.1, "consumer=ledger-service": 43.9, "consumer=payment-service": 66.8, "consumer=account-service": 86.9, "consumer=settlement-service": 141.4} |
| events_consumed_per_s | 420.6163 |
| events_failed | {"category=CONCURRENCY": 1.0194} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 10.0 |
| hikari_acquire_max_ms | 642.6 |
| hikari_acquire_avg_ms | 0.8 |
| hikari_usage_avg_ms | 9.7 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1159.202 |
| pg_rollbacks | 2.0165 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 360.3 |
| mongo_cmd_avg_ms | 1.9 |
| jvm_heap_used_max_mb | 265 |
| jvm_heap_after_gc_max_mb | 111 |
| jvm_heap_committed_max_mb | 280 |
| gc_pause_max_ms | 44.0 |
| gc_pause_total_ms | 2873.6 |
| gc_count | 501.875 |
| alloc_rate_mb_s | 175.9 |
| threads_max | 196.0 |
| process_cpu_avg | 0.7116 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 3.09 | 255 |
| payflow-kafka-1 | 73.2 | 195.27 | 695 |
| payflow-keycloak-1 | 8.9 | 261.02 | 1470 |
| payflow-mongo-1 | 13.4 | 52.19 | 390 |
| payflow-payflow-1 | 134.7 | 207.52 | 694 |
| payflow-postgres-1 | 61.7 | 184.14 | 404 |
| payflow-postgres-exporter-1 | 0.6 | 7.03 | 18 |
| payflow-prometheus-1 | 0.6 | 2.08 | 102 |
| payflow-settlement-rail-1 | 2.8 | 31.77 | 53 |
| payflow-tempo-1 | 1.8 | 12.38 | 111 |
