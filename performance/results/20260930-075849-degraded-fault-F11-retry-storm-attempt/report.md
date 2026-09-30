# Load-test report: 20260930-075849-degraded-fault-F11-retry-storm-attempt

Window: 2026-09-30T07:59:33.545000+00:00 to 2026-09-30T08:03:47.789000+00:00 (+178s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 4771 |
| iterations_per_s | 17.82344066631417 |
| dropped_iterations | 30 |
| payments_accepted | 4771 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 15.09356 |
| post_p95_ms | 223.807206 |
| post_p99_ms | 1475.4311295000018 |
| post_max_ms | 2631.531289 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 18.7646 |
| api_5xx_ratio | None |
| api_post_p50_ms | 13.8 |
| api_post_p95_ms | 169.0 |
| api_post_p99_ms | 332.5 |
| api_get_p99_ms | 70.1 |
| saga_completed | 4783.5861 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 4783.5861} |
| saga_p50_ms | 100700.9 |
| saga_p95_ms | 296263.0 |
| saga_p99_ms | 356767.5 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 6258.6, "step=AWAITING_FUNDS": 7508.3, "step=AWAITING_SETTLEMENT": 211112.0, "step=AWAITING_RISK": 8032.7} |
| drain_seconds_after_load | 178 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 11.0, "step=AWAITING_FUNDS": 13.0, "step=AWAITING_RISK": 4.0, "step=AWAITING_SETTLEMENT": 1257.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 36.0, "step=AWAITING_FUNDS": 49.0, "step=AWAITING_RISK": 105.0, "step=AWAITING_SETTLEMENT": 2674.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 208.0 |
| outbox_publish_delay_p99_ms | 1925.5 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 1569.7, "outbox=payment.outbox_event": 2081.8, "outbox=settlement.outbox_event": 1751.9} |
| outbox_send_p99_ms | 77.8 |
| outbox_published_per_s | 186.6617 |
| outbox_backlog_max | {"outbox=account.outbox_event": 4.0, "outbox=payment.outbox_event": 30.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 1.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 104.0, "group=payment-service,topic=funds.events": 99.0, "group=fraud-service,topic=fraud.commands": 46.0, "group=payment-service,topic=settlement.events": 36.0, "group=account-service,topic=funds.commands": 26.0} |
| consumer_p99_ms | {"consumer=fraud-service": 159.3, "consumer=ledger-service": 60.1, "consumer=payment-service": 109.6, "consumer=account-service": 140.4, "consumer=settlement-service": 307.5} |
| events_consumed_per_s | 167.6605 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 41.3778, "category=CONCURRENCY": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 19.0 |
| hikari_pending_max | 1.0 |
| hikari_acquire_max_ms | 497.8 |
| hikari_acquire_avg_ms | 0.1 |
| hikari_usage_avg_ms | 11.4 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 531.1996 |
| pg_rollbacks | 2.0058 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 2300.3 |
| mongo_cmd_avg_ms | 2.7 |
| jvm_heap_used_max_mb | 233 |
| jvm_heap_after_gc_max_mb | 99 |
| jvm_heap_committed_max_mb | 248 |
| gc_pause_max_ms | 157.0 |
| gc_pause_total_ms | 1743.6 |
| gc_count | 177.2991 |
| alloc_rate_mb_s | 70.8 |
| threads_max | 198.0 |
| process_cpu_avg | 0.5265 |
| process_cpu_max | 0.998 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 0.9 | 2.24 | 189 |
| payflow-kafka-1 | 46.3 | 197.46 | 419 |
| payflow-keycloak-1 | 3.8 | 67.09 | 628 |
| payflow-mongo-1 | 11.8 | 39.08 | 306 |
| payflow-payflow-1 | 100.3 | 192.19 | 592 |
| payflow-postgres-1 | 30.8 | 49.88 | 165 |
| payflow-postgres-exporter-1 | 0.3 | 2.23 | 13 |
| payflow-prometheus-1 | 0.6 | 2.14 | 76 |
| payflow-settlement-rail-1 | 1.9 | 11.21 | 44 |
| payflow-tempo-1 | 0.9 | 3.59 | 84 |
