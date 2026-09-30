# Load-test report: 20260930-080853-smoke-warmup-window400-wsl

Window: 2026-09-30T08:08:59.067000+00:00 to 2026-09-30T08:10:33.595000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.551325515601697 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 21.19616 |
| post_p95_ms | 132.0103525 |
| post_p99_ms | 256.6413955 |
| post_max_ms | 1296.044227 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 4.7822 |
| api_5xx_ratio | None |
| api_post_p50_ms | 19.8 |
| api_post_p95_ms | 121.5 |
| api_post_p99_ms | 240.5 |
| api_get_p99_ms | 27.1 |
| saga_completed | 447.6235 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 447.6235} |
| saga_p50_ms | 1758.4 |
| saga_p95_ms | 3046.9 |
| saga_p99_ms | 3389.9 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 948.5, "step=AWAITING_CAPTURE": 1775.8, "step=AWAITING_FUNDS": 1752.0, "step=AWAITING_SETTLEMENT": 1824.8} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 2.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 6.0, "step=AWAITING_RISK": 3.0, "step=AWAITING_SETTLEMENT": 4.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 236.4 |
| outbox_publish_delay_p99_ms | 870.3 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 897.9, "outbox=payment.outbox_event": 862.2, "outbox=settlement.outbox_event": 1466.9} |
| outbox_send_p99_ms | 44.3 |
| outbox_published_per_s | 52.628 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 6.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 2.0, "group=settlement-service,topic=settlement.commands": 1.0, "group=payment-service,topic=funds.events": 1.0, "group=payment-service,topic=settlement.events": 1.0, "group=account-service,topic=funds.commands": 0.0} |
| consumer_p99_ms | {"consumer=payment-service": 205.7, "consumer=account-service": 260.9, "consumer=settlement-service": 321.7, "consumer=fraud-service": 197.0, "consumer=ledger-service": 172.0} |
| events_consumed_per_s | 47.8013 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 6.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 54.1 |
| hikari_acquire_avg_ms | 0.1 |
| hikari_usage_avg_ms | 15.6 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 162.5934 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 484.4 |
| mongo_cmd_avg_ms | 4.0 |
| jvm_heap_used_max_mb | 187 |
| jvm_heap_after_gc_max_mb | 96 |
| jvm_heap_committed_max_mb | 224 |
| gc_pause_max_ms | 474.0 |
| gc_pause_total_ms | 847.3 |
| gc_count | 31.5424 |
| alloc_rate_mb_s | 30.9 |
| threads_max | 194.0 |
| process_cpu_avg | 0.4036 |
| process_cpu_max | 0.7622 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.3 | 3.71 | 174 |
| payflow-kafka-1 | 64.3 | 194.68 | 554 |
| payflow-keycloak-1 | 0.3 | 0.51 | 608 |
| payflow-mongo-1 | 14.3 | 48.12 | 96 |
| payflow-payflow-1 | 83.7 | 124.05 | 575 |
| payflow-postgres-1 | 13.5 | 17.49 | 102 |
| payflow-postgres-exporter-1 | 0.5 | 2.4 | 12 |
| payflow-prometheus-1 | 0.7 | 2.43 | 81 |
| payflow-settlement-rail-1 | 1.2 | 2.47 | 41 |
| payflow-tempo-1 | 0.4 | 1.23 | 69 |
