# Load-test report: 20260927-160641-normal-final

Window: 2026-09-27T10:36:44.524000+00:00 to 2026-09-27T10:46:44.558000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 12001 |
| iterations_per_s | 19.93992438941055 |
| dropped_iterations | 0 |
| payments_accepted | 12001 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 14.243156 |
| post_p95_ms | 45.683655 |
| post_p99_ms | 112.978486 |
| post_max_ms | 791.084053 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 20.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 13.3 |
| api_post_p95_ms | 43.3 |
| api_post_p99_ms | 103.1 |
| api_get_p99_ms | 10.3 |
| saga_completed | 12020.0492 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 12020.0492} |
| saga_p50_ms | 1660.0 |
| saga_p95_ms | 2118.2 |
| saga_p99_ms | 3355.3 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 702.1, "step=AWAITING_FUNDS": 692.3, "step=AWAITING_RISK": 823.3, "step=AWAITING_SETTLEMENT": 923.4} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 9.0, "step=AWAITING_FUNDS": 7.0, "step=AWAITING_RISK": 7.0, "step=AWAITING_SETTLEMENT": 14.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 28.0, "step=AWAITING_FUNDS": 13.0, "step=AWAITING_RISK": 21.0, "step=AWAITING_SETTLEMENT": 44.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 216.4 |
| outbox_publish_delay_p99_ms | 352.4 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=payment.outbox_event": 353.9, "outbox=settlement.outbox_event": 355.3, "outbox=account.outbox_event": 268.2} |
| outbox_send_p99_ms | 21.2 |
| outbox_published_per_s | 219.7445 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 15.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 16.0, "group=payment-service,topic=fraud.events": 6.0, "group=payment-service,topic=settlement.events": 6.0, "group=payment-service,topic=funds.events": 6.0, "group=settlement-service,topic=settlement.commands": 4.0} |
| consumer_p99_ms | {"consumer=ledger-service": 43.5, "consumer=payment-service": 49.0, "consumer=account-service": 54.2, "consumer=fraud-service": 40.5, "consumer=settlement-service": 80.9} |
| events_consumed_per_s | 199.7849 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 13.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 110.4 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 8.1 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 563.4252 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 25.0 |
| mongo_cmd_max_ms | 341.0 |
| mongo_cmd_avg_ms | 1.5 |
| jvm_heap_used_max_mb | 217 |
| jvm_heap_after_gc_max_mb | 78 |
| jvm_heap_committed_max_mb | 237 |
| gc_pause_max_ms | 17.0 |
| gc_pause_total_ms | 1395.2 |
| gc_count | 382.6063 |
| alloc_rate_mb_s | 88.3 |
| threads_max | 74.0 |
| process_cpu_avg | 0.4116 |
| process_cpu_max | 0.707 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 1.99 | 177 |
| payflow-kafka-1 | 63.4 | 183.71 | 784 |
| payflow-keycloak-1 | 0.2 | 0.26 | 4238 |
| payflow-mongo-1 | 19.0 | 63.9 | 374 |
| payflow-payflow-1 | 110.0 | 166.06 | 600 |
| payflow-postgres-1 | 40.0 | 61.88 | 604 |
| payflow-postgres-exporter-1 | 0.5 | 2.06 | 13 |
| payflow-prometheus-1 | 0.6 | 1.63 | 85 |
| payflow-settlement-rail-1 | 1.0 | 1.29 | 145 |
| payflow-tempo-1 | 0.8 | 3.87 | 811 |
