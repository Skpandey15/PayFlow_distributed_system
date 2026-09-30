# Load-test report: 20260930-054549-burst-window500-wsl

Window: 2026-09-30T05:45:51.939000+00:00 to 2026-09-30T05:52:35.536000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 16698 |
| iterations_per_s | 41.19397055592046 |
| dropped_iterations | 1 |
| payments_accepted | 9480 |
| payments_throttled | 7218 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.6622999906428371 |
| post_p50_ms | 9.8283075 |
| post_p95_ms | 102.60713534999992 |
| post_p99_ms | 288.12853410999946 |
| post_max_ms | 1902.633237 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 23.5825 |
| api_5xx_ratio | 0.4157 |
| api_post_p50_ms | 7.3 |
| api_post_p95_ms | 76.4 |
| api_post_p99_ms | 212.6 |
| api_get_p99_ms | 84.0 |
| saga_completed | 9569.5657 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 9569.5657} |
| saga_p50_ms | 1768.5 |
| saga_p95_ms | 23344.1 |
| saga_p99_ms | 30884.9 |
| saga_step_p99_ms | {"step=AWAITING_SETTLEMENT": 8297.1, "step=AWAITING_RISK": 10609.7, "step=AWAITING_CAPTURE": 7147.6, "step=AWAITING_FUNDS": 8539.6} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 9.0, "step=AWAITING_FUNDS": 9.0, "step=AWAITING_RISK": 3.0, "step=AWAITING_SETTLEMENT": 10.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 188.0, "step=AWAITING_FUNDS": 229.0, "step=AWAITING_RISK": 328.0, "step=AWAITING_SETTLEMENT": 172.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 208.0 |
| outbox_publish_delay_p99_ms | 1254.9 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 557.1, "outbox=payment.outbox_event": 1769.2, "outbox=settlement.outbox_event": 610.2} |
| outbox_send_p99_ms | 52.5 |
| outbox_published_per_s | 259.3748 |
| outbox_backlog_max | {"outbox=account.outbox_event": 2.0, "outbox=payment.outbox_event": 408.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 1.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 321.0, "group=payment-service,topic=fraud.events": 281.0, "group=fraud-service,topic=fraud.commands": 200.0, "group=payment-service,topic=settlement.events": 182.0, "group=account-service,topic=funds.commands": 53.0} |
| consumer_p99_ms | {"consumer=payment-service": 57.8, "consumer=account-service": 72.9, "consumer=settlement-service": 128.4, "consumer=fraud-service": 97.3, "consumer=ledger-service": 44.2} |
| events_consumed_per_s | 235.838 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 32.0 |
| hikari_acquire_max_ms | 1077.2 |
| hikari_acquire_avg_ms | 0.4 |
| hikari_usage_avg_ms | 8.7 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 671.5233 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 295.5 |
| mongo_cmd_avg_ms | 1.8 |
| jvm_heap_used_max_mb | 228 |
| jvm_heap_after_gc_max_mb | 108 |
| jvm_heap_committed_max_mb | 244 |
| gc_pause_max_ms | 47.0 |
| gc_pause_total_ms | 2018.7 |
| gc_count | 344.9317 |
| alloc_rate_mb_s | 92.2 |
| threads_max | 197.0 |
| process_cpu_avg | 0.4813 |
| process_cpu_max | 0.927 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 8.0 | 182 |
| payflow-kafka-1 | 51.1 | 163.47 | 625 |
| payflow-keycloak-1 | 1.0 | 19.1 | 919 |
| payflow-mongo-1 | 12.6 | 61.27 | 204 |
| payflow-payflow-1 | 99.7 | 191.47 | 609 |
| payflow-postgres-1 | 37.8 | 66.63 | 262 |
| payflow-postgres-exporter-1 | 0.3 | 1.93 | 15 |
| payflow-prometheus-1 | 0.4 | 1.58 | 100 |
| payflow-settlement-rail-1 | 1.9 | 6.99 | 39 |
| payflow-tempo-1 | 0.8 | 4.97 | 107 |
