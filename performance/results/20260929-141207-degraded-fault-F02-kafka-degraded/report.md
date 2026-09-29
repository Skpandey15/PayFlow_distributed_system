# Load-test report: 20260929-141207-degraded-fault-F02-kafka-degraded

Window: 2026-09-29T08:42:13.814000+00:00 to 2026-09-29T08:45:13.822000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3598 |
| iterations_per_s | 19.48063201093742 |
| dropped_iterations | 2 |
| payments_accepted | 3598 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 25.3637895 |
| post_p95_ms | 326.84128284999986 |
| post_p99_ms | 630.5709756399998 |
| post_max_ms | 1381.463455 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 19.9885 |
| api_5xx_ratio | None |
| api_post_p50_ms | 21.0 |
| api_post_p95_ms | 195.0 |
| api_post_p99_ms | 343.6 |
| api_get_p99_ms | 175.5 |
| saga_completed | 3689.9592 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3689.9592} |
| saga_p50_ms | 57166.2 |
| saga_p95_ms | 109215.5 |
| saga_p99_ms | 113469.1 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 55650.8, "step=AWAITING_FUNDS": 57571.5, "step=AWAITING_RISK": 61749.3, "step=AWAITING_SETTLEMENT": 39611.2} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 573.0, "step=AWAITING_FUNDS": 192.0, "step=AWAITING_RISK": 177.0, "step=AWAITING_SETTLEMENT": 679.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 573.0, "step=AWAITING_FUNDS": 759.0, "step=AWAITING_RISK": 1089.0, "step=AWAITING_SETTLEMENT": 679.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 213.0 |
| outbox_publish_delay_p99_ms | 4664.2 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 4027.0, "outbox=payment.outbox_event": 4833.8, "outbox=settlement.outbox_event": 2148.8} |
| outbox_send_p99_ms | 404.3 |
| outbox_published_per_s | 202.311 |
| outbox_backlog_max | {"outbox=account.outbox_event": 6.0, "outbox=payment.outbox_event": 155.0, "outbox=settlement.outbox_event": 4.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 2.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 1357.0, "group=payment-service,topic=fraud.events": 949.0, "group=fraud-service,topic=fraud.commands": 552.0, "group=payment-service,topic=settlement.events": 544.0, "group=account-service,topic=funds.commands": 224.0} |
| consumer_p99_ms | {"consumer=ledger-service": 82.3, "consumer=payment-service": 104.8, "consumer=account-service": 125.6, "consumer=fraud-service": 405.6, "consumer=settlement-service": 320.7} |
| events_consumed_per_s | 184.3032 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 16.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 424.5 |
| hikari_acquire_avg_ms | 0.1 |
| hikari_usage_avg_ms | 15.0 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 541.2343 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 326.9 |
| mongo_cmd_avg_ms | 4.2 |
| jvm_heap_used_max_mb | 288 |
| jvm_heap_after_gc_max_mb | 101 |
| jvm_heap_committed_max_mb | 364 |
| gc_pause_max_ms | 63.0 |
| gc_pause_total_ms | 1375.8 |
| gc_count | 155.5432 |
| alloc_rate_mb_s | 82.4 |
| threads_max | 195.0 |
| process_cpu_avg | 0.7335 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.3 | 1.98 | 338 |
| payflow-kafka-1 | 37.0 | 125.04 | 1120 |
| payflow-keycloak-1 | 0.4 | 1.23 | 756 |
| payflow-mongo-1 | 13.8 | 55.16 | 325 |
| payflow-payflow-1 | 148.6 | 208.32 | 717 |
| payflow-postgres-1 | 45.2 | 103.45 | 338 |
| payflow-postgres-exporter-1 | 0.3 | 2.15 | 10 |
| payflow-prometheus-1 | 1.0 | 2.34 | 81 |
| payflow-settlement-rail-1 | 1.1 | 4.17 | 46 |
| payflow-tempo-1 | 1.7 | 7.42 | 729 |
