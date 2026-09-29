# Load-test report: 20260927-104454-normal-baseline

Window: 2026-09-27T05:14:57.865000+00:00 to 2026-09-27T05:24:57.888000+00:00 (+519s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 12000 |
| iterations_per_s | 19.94138198675985 |
| dropped_iterations | 0 |
| payments_accepted | 12000 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 7.7439215 |
| post_p95_ms | 18.4370537 |
| post_p99_ms | 36.829629020000006 |
| post_max_ms | 455.676898 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 20.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 7.4 |
| api_post_p95_ms | 18.6 |
| api_post_p99_ms | 36.3 |
| api_get_p99_ms | 4.9 |
| saga_completed | 12042.0457 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 12042.0457} |
| saga_p50_ms | 404048.6 |
| saga_p95_ms | 538844.5 |
| saga_p99_ms | 547573.6 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 158487.1, "step=AWAITING_FUNDS": 159120.3, "step=AWAITING_RISK": 156435.0, "step=AWAITING_SETTLEMENT": 158976.6} |
| drain_seconds_after_load | 519 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 1226.0, "step=AWAITING_FUNDS": 1848.0, "step=AWAITING_RISK": 2313.0, "step=AWAITING_SETTLEMENT": 1476.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 2335.0, "step=AWAITING_FUNDS": 2347.0, "step=AWAITING_RISK": 2313.0, "step=AWAITING_SETTLEMENT": 2308.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 57686.9 |
| outbox_publish_delay_p99_ms | 157977.5 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 983.6, "outbox=payment.outbox_event": 158648.1, "outbox=settlement.outbox_event": 713.2} |
| outbox_send_p99_ms | 8.1 |
| outbox_published_per_s | 128.1496 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 13672.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 145.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=settlement.events": 4.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=account-service": 30.9, "consumer=fraud-service": 20.7, "consumer=ledger-service": 17.7, "consumer=payment-service": 29.0, "consumer=settlement-service": 36.8} |
| events_consumed_per_s | 123.9714 |
| events_failed | {"category=CONCURRENCY": 1.0145} |
| events_dead_lettered | 0 |
| hikari_active_max | 5.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 4.5 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 8.2 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 404.1395 |
| pg_rollbacks | 3.0243 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 49.8 |
| mongo_cmd_avg_ms | 0.6 |
| jvm_heap_used_max_mb | 171 |
| jvm_heap_after_gc_max_mb | 109 |
| jvm_heap_committed_max_mb | 223 |
| gc_pause_max_ms | 190.0 |
| gc_pause_total_ms | 3976.2 |
| gc_count | 1076.8493 |
| alloc_rate_mb_s | 67.4 |
| threads_max | 191.0 |
| process_cpu_avg | 0.3156 |
| process_cpu_max | 0.428 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 0.9 | 1.63 | 319 |
| payflow-kafka-1 | 50.2 | 194.64 | 783 |
| payflow-keycloak-1 | 0.8 | 32.52 | 664 |
| payflow-mongo-1 | 10.6 | 57.02 | 486 |
| payflow-payflow-1 | 63.6 | 90.79 | 605 |
| payflow-postgres-1 | 22.3 | 94.85 | 358 |
| payflow-postgres-exporter-1 | 0.3 | 2.95 | 10 |
| payflow-prometheus-1 | 0.4 | 1.58 | 54 |
| payflow-tempo-1 | 0.7 | 3.49 | 62 |
