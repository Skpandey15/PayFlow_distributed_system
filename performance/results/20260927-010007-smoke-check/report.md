# Load-test report: 20260927-010007-smoke-check

Window: 2026-09-26T19:30:17.386000+00:00 to 2026-09-26T19:30:47.414000+00:00 (+15s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 150 |
| iterations_per_s | 4.022549930703781 |
| dropped_iterations | 0 |
| payments_accepted | 150 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 60.834385999999995 |
| post_p95_ms | 329.44221499999946 |
| post_p99_ms | 802.7221351399982 |
| post_max_ms | 1071.523979 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 53.5 |
| api_post_p95_ms | 232.1 |
| api_post_p99_ms | 436.2 |
| api_get_p99_ms | 118.8 |
| saga_completed | 159.3983 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 130.764, "outcome=FAILED": 28.6343} |
| saga_p50_ms | 5714.7 |
| saga_p95_ms | 7613.1 |
| saga_p99_ms | 8394.6 |
| saga_step_p99_ms | {"step=COMPENSATING": 3489.7, "step=AWAITING_CAPTURE": 2822.5, "step=AWAITING_FUNDS": 2804.2, "step=AWAITING_RISK": 1895.2, "step=AWAITING_SETTLEMENT": 2473.9} |
| saga_open_max | {"step=AWAITING_CAPTURE": 12.0, "step=AWAITING_FUNDS": 10.0, "step=AWAITING_RISK": 4.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 4.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 723.1 |
| outbox_publish_delay_p99_ms | 1707.1 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 1641.3, "outbox=payment.outbox_event": 1727.6, "outbox=settlement.outbox_event": 1418.9} |
| outbox_send_p99_ms | 44.6 |
| outbox_published_per_s | 48.8931 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 25.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 0.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=ledger-service": 314.7, "consumer=payment-service": 350.4, "consumer=account-service": 340.1, "consumer=fraud-service": 89.0, "consumer=settlement-service": 917.3} |
| events_consumed_per_s | 44.3747 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 6.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 204.1 |
| hikari_acquire_avg_ms | 0.2 |
| hikari_usage_avg_ms | 55.4 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 137.28 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 92.0 |
| mongo_cmd_avg_ms | 2.6 |
| jvm_heap_used_max_mb | 144 |
| jvm_heap_after_gc_max_mb | 91 |
| jvm_heap_committed_max_mb | 216 |
| gc_pause_max_ms | 1003.0 |
| gc_pause_total_ms | 306.0 |
| gc_count | 24.75 |
| alloc_rate_mb_s | 37.4 |
| threads_max | 187.0 |
| process_cpu_avg | 0.6709 |
| process_cpu_max | 0.7941 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.8 | 2.0 | 381 |
| payflow-kafka-1 | 88.5 | 156.47 | 528 |
| payflow-keycloak-1 | 0.3 | 0.33 | 855 |
| payflow-mongo-1 | 6.6 | 9.65 | 238 |
| payflow-payflow-1 | 136.3 | 155.53 | 508 |
| payflow-postgres-1 | 53.5 | 73.19 | 101 |
| payflow-postgres-exporter-1 | 1.7 | 5.16 | 9 |
| payflow-prometheus-1 | 1.6 | 3.06 | 75 |
| payflow-tempo-1 | 1.9 | 3.49 | 51 |
