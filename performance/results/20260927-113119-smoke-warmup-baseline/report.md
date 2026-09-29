# Load-test report: 20260927-113119-smoke-warmup-baseline

Window: 2026-09-27T06:01:24.500000+00:00 to 2026-09-27T06:02:54.503000+00:00 (+30s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 450 |
| iterations_per_s | 4.762868751772707 |
| dropped_iterations | 0 |
| payments_accepted | 450 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 14.069211 |
| post_p95_ms | 29.53508025 |
| post_p99_ms | 42.431714629999945 |
| post_max_ms | 177.954375 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 13.3 |
| api_post_p95_ms | 27.8 |
| api_post_p99_ms | 38.2 |
| api_get_p99_ms | 8.2 |
| saga_completed | 475.704 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 475.704} |
| saga_p50_ms | 1728.1 |
| saga_p95_ms | 2131.2 |
| saga_p99_ms | 2396.2 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 759.9, "step=AWAITING_FUNDS": 803.2, "step=AWAITING_RISK": 443.6, "step=AWAITING_SETTLEMENT": 632.1} |
| drain_seconds_after_load | 30 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 4.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 4.0, "step=AWAITING_FUNDS": 4.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 2.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 261.4 |
| outbox_publish_delay_p99_ms | 440.3 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=payment.outbox_event": 441.4, "outbox=settlement.outbox_event": 440.0, "outbox=account.outbox_event": 425.8} |
| outbox_send_p99_ms | 12.1 |
| outbox_published_per_s | 54.6544 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 12.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 0.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=account-service": 25.2, "consumer=fraud-service": 22.4, "consumer=settlement-service": 20.2, "consumer=ledger-service": 16.4, "consumer=payment-service": 20.9} |
| events_consumed_per_s | 49.6597 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 2.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 7.6 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 8.6 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 153.3765 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 48.6 |
| mongo_cmd_avg_ms | 0.7 |
| jvm_heap_used_max_mb | 149 |
| jvm_heap_after_gc_max_mb | 90 |
| jvm_heap_committed_max_mb | 212 |
| gc_pause_max_ms | 199.0 |
| gc_pause_total_ms | 308.7 |
| gc_count | 60.0 |
| alloc_rate_mb_s | 31.6 |
| threads_max | 189.0 |
| process_cpu_avg | 0.3801 |
| process_cpu_max | 0.7735 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 1.46 | 320 |
| payflow-kafka-1 | 64.2 | 175.54 | 575 |
| payflow-keycloak-1 | 0.5 | 1.74 | 685 |
| payflow-mongo-1 | 8.6 | 42.15 | 204 |
| payflow-payflow-1 | 71.9 | 155.47 | 516 |
| payflow-postgres-1 | 12.9 | 17.05 | 106 |
| payflow-postgres-exporter-1 | 0.6 | 2.06 | 8 |
| payflow-prometheus-1 | 0.8 | 3.65 | 76 |
| payflow-tempo-1 | 0.6 | 2.07 | 99 |
