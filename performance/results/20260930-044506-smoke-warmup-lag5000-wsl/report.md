# Load-test report: 20260930-044506-smoke-warmup-lag5000-wsl

Window: 2026-09-30T04:45:11.366000+00:00 to 2026-09-30T04:46:46.834000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.517984373275912 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 13.773684 |
| post_p95_ms | 30.9612895 |
| post_p99_ms | 87.930392 |
| post_max_ms | 102.863079 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 4.699 |
| api_5xx_ratio | None |
| api_post_p50_ms | 12.6 |
| api_post_p95_ms | 26.7 |
| api_post_p99_ms | 55.6 |
| api_get_p99_ms | 18.6 |
| saga_completed | 458.2508 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 458.2508} |
| saga_p50_ms | 1633.3 |
| saga_p95_ms | 2896.4 |
| saga_p99_ms | 3523.0 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 1965.8, "step=AWAITING_FUNDS": 2248.9, "step=AWAITING_SETTLEMENT": 2311.7, "step=AWAITING_RISK": 350.1} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 1.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 225.5 |
| outbox_publish_delay_p99_ms | 351.2 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=settlement.outbox_event": 433.5, "outbox=account.outbox_event": 259.2, "outbox=payment.outbox_event": 351.4} |
| outbox_send_p99_ms | 20.0 |
| outbox_published_per_s | 51.7269 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 1.0, "group=settlement-service,topic=settlement.commands": 1.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0, "group=account-service,topic=funds.commands": 0.0} |
| consumer_p99_ms | {"consumer=fraud-service": 44.0, "consumer=ledger-service": 19.8, "consumer=payment-service": 30.7, "consumer=account-service": 48.4, "consumer=settlement-service": 64.8} |
| events_consumed_per_s | 46.9831 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 4.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 54.5 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 6.1 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 155.5106 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 87.8 |
| mongo_cmd_avg_ms | 1.4 |
| jvm_heap_used_max_mb | 221 |
| jvm_heap_after_gc_max_mb | 97 |
| jvm_heap_committed_max_mb | 242 |
| gc_pause_max_ms | 37.0 |
| gc_pause_total_ms | 293.3 |
| gc_count | 27.3149 |
| alloc_rate_mb_s | 30.6 |
| threads_max | 193.0 |
| process_cpu_avg | 0.3594 |
| process_cpu_max | 0.6667 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.46 | 255 |
| payflow-kafka-1 | 57.8 | 140.18 | 566 |
| payflow-keycloak-1 | 0.2 | 0.25 | 741 |
| payflow-mongo-1 | 14.7 | 52.53 | 218 |
| payflow-payflow-1 | 75.0 | 118.1 | 640 |
| payflow-postgres-1 | 10.8 | 13.06 | 112 |
| payflow-postgres-exporter-1 | 0.2 | 1.67 | 10 |
| payflow-prometheus-1 | 0.6 | 1.6 | 93 |
| payflow-settlement-rail-1 | 1.9 | 6.63 | 41 |
| payflow-tempo-1 | 0.5 | 0.97 | 91 |
