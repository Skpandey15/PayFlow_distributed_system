# Load-test report: 20260927-181454-smoke-warmup-final

Window: 2026-09-27T12:46:30.957000+00:00 to 2026-09-27T12:48:00.977000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.802395405166117 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 17.863194 |
| post_p95_ms | 48.2594565 |
| post_p99_ms | 70.09554700000001 |
| post_max_ms | 152.591491 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 16.7 |
| api_post_p95_ms | 43.5 |
| api_post_p99_ms | 60.4 |
| api_get_p99_ms | 21.9 |
| saga_completed | 459.2261 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 459.2261} |
| saga_p50_ms | 1628.8 |
| saga_p95_ms | 1964.0 |
| saga_p99_ms | 2110.8 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 583.1, "step=AWAITING_RISK": 351.1, "step=AWAITING_SETTLEMENT": 697.3, "step=AWAITING_CAPTURE": 568.7} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 2.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 4.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 231.3 |
| outbox_publish_delay_p99_ms | 347.5 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 245.7, "outbox=payment.outbox_event": 349.5, "outbox=settlement.outbox_event": 349.1} |
| outbox_send_p99_ms | 26.5 |
| outbox_published_per_s | 54.6771 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=settlement.events": 1.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=account-service": 52.7, "consumer=fraud-service": 48.5, "consumer=settlement-service": 67.8, "consumer=ledger-service": 27.0, "consumer=payment-service": 37.8} |
| events_consumed_per_s | 49.6986 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 5.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 86.2 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 8.9 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 157.2824 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 48.6 |
| mongo_cmd_avg_ms | 2.1 |
| jvm_heap_used_max_mb | 139 |
| jvm_heap_after_gc_max_mb | 72 |
| jvm_heap_committed_max_mb | 163 |
| gc_pause_max_ms | 23.0 |
| gc_pause_total_ms | 300.9 |
| gc_count | 36.8196 |
| alloc_rate_mb_s | 27.8 |
| threads_max | 72.0 |
| process_cpu_avg | 0.401 |
| process_cpu_max | 0.644 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
