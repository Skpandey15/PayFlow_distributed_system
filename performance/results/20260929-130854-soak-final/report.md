# Load-test report: 20260929-130854-soak-final

Window: 2026-09-29T07:38:57.216000+00:00 to 2026-09-29T08:23:57.310000+00:00 (+30s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 53997 |
| iterations_per_s | 19.98533884800278 |
| dropped_iterations | 3 |
| payments_accepted | 53997 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 12.740592 |
| post_p95_ms | 32.806371 |
| post_p99_ms | 58.761122560000096 |
| post_max_ms | 1139.630825 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 19.9993 |
| api_5xx_ratio | None |
| api_post_p50_ms | 11.6 |
| api_post_p95_ms | 30.8 |
| api_post_p99_ms | 55.7 |
| api_get_p99_ms | 8.7 |
| saga_completed | 54080.0477 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 54080.0477} |
| saga_p50_ms | 1638.3 |
| saga_p95_ms | 2043.4 |
| saga_p99_ms | 2259.2 |
| saga_step_p99_ms | {"step=AWAITING_SETTLEMENT": 793.0, "step=AWAITING_CAPTURE": 625.0, "step=AWAITING_FUNDS": 623.5, "step=AWAITING_RISK": 434.6} |
| drain_seconds_after_load | 30 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 10.0, "step=AWAITING_FUNDS": 9.0, "step=AWAITING_RISK": 4.0, "step=AWAITING_SETTLEMENT": 10.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 18.0, "step=AWAITING_FUNDS": 15.0, "step=AWAITING_RISK": 29.0, "step=AWAITING_SETTLEMENT": 20.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 218.9 |
| outbox_publish_delay_p99_ms | 347.6 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 265.4, "outbox=payment.outbox_event": 349.5, "outbox=settlement.outbox_event": 351.6} |
| outbox_send_p99_ms | 29.9 |
| outbox_published_per_s | 220.0085 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 19.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 32.0, "group=settlement-service,topic=settlement.commands": 26.0, "group=account-service,topic=funds.commands": 21.0, "group=payment-service,topic=settlement.events": 15.0, "group=payment-service,topic=fraud.events": 8.0} |
| consumer_p99_ms | {"consumer=account-service": 48.2, "consumer=fraud-service": 33.3, "consumer=settlement-service": 69.4, "consumer=ledger-service": 37.3, "consumer=payment-service": 43.7} |
| events_consumed_per_s | 200.003 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 15.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 515.7 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 6.9 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 563.0252 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 78.4 |
| mongo_cmd_avg_ms | 1.4 |
| jvm_heap_used_max_mb | 180 |
| jvm_heap_after_gc_max_mb | 94 |
| jvm_heap_committed_max_mb | 192 |
| gc_pause_max_ms | 41.0 |
| gc_pause_total_ms | 12771.4 |
| gc_count | 2816.1578 |
| alloc_rate_mb_s | 87.2 |
| threads_max | 199.0 |
| process_cpu_avg | 0.4217 |
| process_cpu_max | 0.693 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 7.09 | 340 |
| payflow-kafka-1 | 54.4 | 191.98 | 1133 |
| payflow-keycloak-1 | 0.5 | 19.55 | 753 |
| payflow-mongo-1 | 14.1 | 68.46 | 382 |
| payflow-payflow-1 | 83.6 | 129.95 | 566 |
| payflow-postgres-1 | 38.1 | 116.0 | 758 |
| payflow-postgres-exporter-1 | 0.4 | 2.96 | 10 |
| payflow-prometheus-1 | 0.6 | 2.32 | 92 |
| payflow-settlement-rail-1 | 1.0 | 9.26 | 47 |
| payflow-tempo-1 | 1.3 | 43.67 | 1250 |
