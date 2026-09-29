# Load-test report: 20260927-174253-smoke-warmup-final

Window: 2026-09-27T12:12:57.646000+00:00 to 2026-09-27T12:14:27.648000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 450 |
| iterations_per_s | 4.795499773546798 |
| dropped_iterations | 0 |
| payments_accepted | 450 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 17.872201 |
| post_p95_ms | 44.27045174999998 |
| post_p99_ms | 85.04150922999997 |
| post_max_ms | 152.852024 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 16.4 |
| api_post_p95_ms | 37.6 |
| api_post_p99_ms | 76.0 |
| api_get_p99_ms | 9.5 |
| saga_completed | 464.8521 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 464.8521} |
| saga_p50_ms | 1623.8 |
| saga_p95_ms | 1937.9 |
| saga_p99_ms | 2121.9 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 609.6, "step=AWAITING_FUNDS": 613.0, "step=AWAITING_RISK": 355.2, "step=AWAITING_SETTLEMENT": 695.5} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 4.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 1.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 4.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 4.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 230.7 |
| outbox_publish_delay_p99_ms | 349.9 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 264.8, "outbox=payment.outbox_event": 351.6, "outbox=settlement.outbox_event": 353.4} |
| outbox_send_p99_ms | 25.0 |
| outbox_published_per_s | 54.6672 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 0.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=payment-service": 44.2, "consumer=account-service": 57.3, "consumer=fraud-service": 42.8, "consumer=settlement-service": 78.1, "consumer=ledger-service": 30.3} |
| events_consumed_per_s | 49.622 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 5.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 18.4 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 8.3 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 159.8125 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 48.4 |
| mongo_cmd_avg_ms | 1.6 |
| jvm_heap_used_max_mb | 181 |
| jvm_heap_after_gc_max_mb | 74 |
| jvm_heap_committed_max_mb | 201 |
| gc_pause_max_ms | 70.0 |
| gc_pause_total_ms | 212.7 |
| gc_count | 25.268 |
| alloc_rate_mb_s | 29.0 |
| threads_max | 72.0 |
| process_cpu_avg | 0.3592 |
| process_cpu_max | 0.6418 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 0.99 | 332 |
| payflow-kafka-1 | 23.8 | 23.75 | 447 |
| payflow-mongo-1 | 1.8 | 1.83 | 204 |
| payflow-postgres-exporter-1 | 0.0 | 0.0 | 8 |
| payflow-prometheus-1 | 0.6 | 0.56 | 115 |
| payflow-tempo-1 | 0.3 | 0.3 | 54 |
