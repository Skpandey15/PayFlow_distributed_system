# Load-test report: 20260930-062250-smoke-warmup-window300-wsl

Window: 2026-09-30T06:22:56.438000+00:00 to 2026-09-30T06:24:32.309000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 450 |
| iterations_per_s | 4.467791737526963 |
| dropped_iterations | 0 |
| payments_accepted | 450 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 15.808598499999999 |
| post_p95_ms | 35.53876714999995 |
| post_p99_ms | 75.58217364999997 |
| post_max_ms | 93.255828 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 4.6784 |
| api_5xx_ratio | None |
| api_post_p50_ms | 14.6 |
| api_post_p95_ms | 31.9 |
| api_post_p99_ms | 46.8 |
| api_get_p99_ms | 9.6 |
| saga_completed | 447.8459 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 447.8459} |
| saga_p50_ms | 1634.0 |
| saga_p95_ms | 3314.7 |
| saga_p99_ms | 3833.7 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 351.0, "step=AWAITING_CAPTURE": 2247.1, "step=AWAITING_FUNDS": 2286.6, "step=AWAITING_SETTLEMENT": 2427.3} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 2.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 4.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 4.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 227.3 |
| outbox_publish_delay_p99_ms | 357.0 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 267.5, "outbox=payment.outbox_event": 357.8, "outbox=settlement.outbox_event": 419.7} |
| outbox_send_p99_ms | 23.4 |
| outbox_published_per_s | 51.5271 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 6.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=payment-service,topic=funds.events": 0.0, "group=account-service,topic=funds.commands": 0.0} |
| consumer_p99_ms | {"consumer=account-service": 47.4, "consumer=settlement-service": 55.4, "consumer=fraud-service": 38.8, "consumer=ledger-service": 20.8, "consumer=payment-service": 30.2} |
| events_consumed_per_s | 46.8377 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 4.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 95.7 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 6.8 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 158.6601 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 53.5 |
| mongo_cmd_avg_ms | 1.7 |
| jvm_heap_used_max_mb | 182 |
| jvm_heap_after_gc_max_mb | 97 |
| jvm_heap_committed_max_mb | 194 |
| gc_pause_max_ms | 18.0 |
| gc_pause_total_ms | 321.7 |
| gc_count | 37.2471 |
| alloc_rate_mb_s | 30.3 |
| threads_max | 193.0 |
| process_cpu_avg | 0.3983 |
| process_cpu_max | 0.7392 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 1.3 | 175 |
| payflow-kafka-1 | 44.6 | 180.39 | 576 |
| payflow-keycloak-1 | 0.3 | 0.99 | 1153 |
| payflow-mongo-1 | 12.3 | 50.13 | 254 |
| payflow-payflow-1 | 84.6 | 114.82 | 542 |
| payflow-postgres-1 | 14.0 | 18.76 | 103 |
| payflow-postgres-exporter-1 | 0.6 | 2.32 | 13 |
| payflow-prometheus-1 | 0.7 | 1.9 | 120 |
| payflow-settlement-rail-1 | 2.4 | 9.68 | 37 |
| payflow-tempo-1 | 0.5 | 1.38 | 82 |
