# Load-test report: 20260930-045640-smoke-warmup-lag5000-wsl

Window: 2026-09-30T04:56:44.907000+00:00 to 2026-09-30T04:58:18.441000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 450 |
| iterations_per_s | 4.625583631436858 |
| dropped_iterations | 0 |
| payments_accepted | 450 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 13.389736 |
| post_p95_ms | 26.543659899999938 |
| post_p99_ms | 45.08689045 |
| post_max_ms | 111.58622 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 4.6167 |
| api_5xx_ratio | None |
| api_post_p50_ms | 12.2 |
| api_post_p95_ms | 22.3 |
| api_post_p99_ms | 37.3 |
| api_get_p99_ms | 6.4 |
| saga_completed | 444.7389 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 444.7389} |
| saga_p50_ms | 1618.8 |
| saga_p95_ms | 1898.9 |
| saga_p99_ms | 3470.5 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 344.9, "step=AWAITING_CAPTURE": 2201.2, "step=AWAITING_FUNDS": 2204.7, "step=AWAITING_SETTLEMENT": 2202.6} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 2.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 2.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 225.1 |
| outbox_publish_delay_p99_ms | 333.5 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 266.8, "outbox=payment.outbox_event": 335.6, "outbox=settlement.outbox_event": 342.2} |
| outbox_send_p99_ms | 18.9 |
| outbox_published_per_s | 50.8527 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 5.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=payment-service,topic=funds.events": 0.0, "group=account-service,topic=funds.commands": 0.0} |
| consumer_p99_ms | {"consumer=fraud-service": 35.8, "consumer=ledger-service": 16.1, "consumer=payment-service": 22.9, "consumer=account-service": 27.7, "consumer=settlement-service": 33.4} |
| events_consumed_per_s | 46.236 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 3.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 4.1 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 5.6 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 158.7037 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 52.9 |
| mongo_cmd_avg_ms | 1.2 |
| jvm_heap_used_max_mb | 259 |
| jvm_heap_after_gc_max_mb | 100 |
| jvm_heap_committed_max_mb | 306 |
| gc_pause_max_ms | 23.0 |
| gc_pause_total_ms | 222.9 |
| gc_count | 14.6502 |
| alloc_rate_mb_s | 27.8 |
| threads_max | 194.0 |
| process_cpu_avg | 0.3202 |
| process_cpu_max | 0.5888 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 0.9 | 1.12 | 218 |
| payflow-kafka-1 | 45.6 | 175.34 | 563 |
| payflow-keycloak-1 | 0.3 | 0.32 | 754 |
| payflow-mongo-1 | 7.3 | 42.35 | 234 |
| payflow-payflow-1 | 66.3 | 89.39 | 715 |
| payflow-postgres-1 | 11.1 | 14.69 | 112 |
| payflow-postgres-exporter-1 | 0.3 | 1.9 | 11 |
| payflow-prometheus-1 | 0.5 | 1.28 | 68 |
| payflow-settlement-rail-1 | 1.8 | 7.09 | 40 |
| payflow-tempo-1 | 0.4 | 0.85 | 87 |
