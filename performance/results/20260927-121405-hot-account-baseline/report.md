# Load-test report: 20260927-121405-hot-account-baseline

Window: 2026-09-27T06:44:08.028000+00:00 to 2026-09-27T06:53:38.125000+00:00 (+342s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 85445 |
| iterations_per_s | 149.3915041663347 |
| dropped_iterations | 505 |
| payments_accepted | 84507 |
| payments_throttled | 0 |
| payments_rejected_at_api | 72 |
| checks_pass_rate | 0.9992208773393355 |
| post_p50_ms | 7.644521 |
| post_p95_ms | 46.80469069999994 |
| post_p99_ms | 370.1233285400005 |
| post_max_ms | 7253.154546 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 149.3929 |
| api_5xx_ratio | 0.0001 |
| api_post_p50_ms | 7.3 |
| api_post_p95_ms | 43.4 |
| api_post_p99_ms | 314.1 |
| api_get_p99_ms | 82.4 |
| saga_completed | 2675.8681 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 2675.8681} |
| saga_p50_ms | 229523.1 |
| saga_p95_ms | 600000.0 |
| saga_p99_ms | 600000.0 |
| saga_step_p99_ms | {"step=AWAITING_SETTLEMENT": 538737.4, "step=AWAITING_CAPTURE": 543183.7, "step=AWAITING_FUNDS": 543638.5, "step=AWAITING_RISK": 549147.2} |
| drain_seconds_after_load | 342 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 1486.0, "step=AWAITING_FUNDS": 9388.0, "step=AWAITING_RISK": 68450.0, "step=AWAITING_SETTLEMENT": 2703.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 1988.0, "step=AWAITING_FUNDS": 21088.0, "step=AWAITING_RISK": 54934.0, "step=AWAITING_SETTLEMENT": 3842.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 1988.0, "step=AWAITING_FUNDS": 21088.0, "step=AWAITING_RISK": 68450.0, "step=AWAITING_SETTLEMENT": 3842.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 184403.7 |
| outbox_publish_delay_p99_ms | 547294.6 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=settlement.outbox_event": 996.5, "outbox=account.outbox_event": 1053.0, "outbox=payment.outbox_event": 548358.3} |
| outbox_send_p99_ms | 12.2 |
| outbox_published_per_s | 114.4319 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 158746.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 553.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 32.0, "group=payment-service,topic=settlement.events": 15.0, "group=account-service,topic=funds.commands": 10.0, "group=payment-service,topic=funds.events": 9.0, "group=settlement-service,topic=settlement.commands": 2.0} |
| consumer_p99_ms | {"consumer=ledger-service": 25.4, "consumer=payment-service": 43.7, "consumer=account-service": 69.0, "consumer=fraud-service": 68.1, "consumer=settlement-service": 84.4} |
| events_consumed_per_s | 115.6348 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 0.0, "category=CONCURRENCY": 7.0345} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 143.0 |
| hikari_acquire_max_ms | 5043.8 |
| hikari_acquire_avg_ms | 3.0 |
| hikari_usage_avg_ms | 4.8 |
| hikari_timeouts | 107.035 |
| pg_commits_per_s | 1524.0991 |
| pg_rollbacks | 12.0319 |
| pg_deadlocks | 0 |
| pg_backends_max | 24.0 |
| mongo_cmd_max_ms | 433.7 |
| mongo_cmd_avg_ms | 1.8 |
| jvm_heap_used_max_mb | 226 |
| jvm_heap_after_gc_max_mb | 164 |
| jvm_heap_committed_max_mb | 234 |
| gc_pause_max_ms | 1737.0 |
| gc_pause_total_ms | 9655.2 |
| gc_count | 1612.5363 |
| alloc_rate_mb_s | 146.7 |
| threads_max | 189.0 |
| process_cpu_avg | 0.5839 |
| process_cpu_max | 0.8569 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 7.29 | 320 |
| payflow-kafka-1 | 74.0 | 417.04 | 692 |
| payflow-keycloak-1 | 15.2 | 697.99 | 2607 |
| payflow-mongo-1 | 22.0 | 137.83 | 372 |
| payflow-payflow-1 | 122.4 | 355.59 | 629 |
| payflow-postgres-1 | 55.9 | 393.51 | 554 |
| payflow-postgres-exporter-1 | 0.5 | 10.31 | 9 |
| payflow-prometheus-1 | 0.6 | 6.21 | 70 |
| payflow-tempo-1 | 1.7 | 11.97 | 197 |
