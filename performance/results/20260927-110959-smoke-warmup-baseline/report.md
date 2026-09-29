# Load-test report: 20260927-110959-smoke-warmup-baseline

Window: 2026-09-27T05:40:05.185000+00:00 to 2026-09-27T05:41:35.190000+00:00 (+47s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 450 |
| iterations_per_s | 4.734171058842401 |
| dropped_iterations | 0 |
| payments_accepted | 450 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 13.9577885 |
| post_p95_ms | 36.2108852 |
| post_p99_ms | 71.48358308999997 |
| post_max_ms | 197.018745 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | None |
| api_5xx_ratio | None |
| api_post_p50_ms | None |
| api_post_p95_ms | None |
| api_post_p99_ms | None |
| api_get_p99_ms | None |
| saga_completed | 0.0 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 0.0} |
| saga_p50_ms | None |
| saga_p95_ms | None |
| saga_p99_ms | None |
| saga_step_p99_ms | {} |
| drain_seconds_after_load | 47 |
| saga_open_at_load_end | {} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | None |
| outbox_publish_delay_p99_ms | None |
| outbox_publish_delay_p99_by_outbox_ms | {} |
| outbox_send_p99_ms | None |
| outbox_published_per_s | None |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 0.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {} |
| events_consumed_per_s | None |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 0.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 4.1 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 1.2 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 185.0588 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 109.3 |
| mongo_cmd_avg_ms | None |
| jvm_heap_used_max_mb | 116 |
| jvm_heap_after_gc_max_mb | 93 |
| jvm_heap_committed_max_mb | 218 |
| gc_pause_max_ms | 197.0 |
| gc_pause_total_ms | 19.3 |
| gc_count | 2.9728 |
| alloc_rate_mb_s | None |
| threads_max | 188.0 |
| process_cpu_avg | None |
| process_cpu_max | None |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.15 | 320 |
| payflow-kafka-1 | 48.4 | 175.41 | 588 |
| payflow-keycloak-1 | 5.4 | 64.08 | 766 |
| payflow-mongo-1 | 6.2 | 41.45 | 366 |
| payflow-payflow-1 | 71.8 | 126.06 | 530 |
| payflow-postgres-1 | 12.2 | 21.99 | 106 |
| payflow-postgres-exporter-1 | 0.6 | 2.26 | 9 |
| payflow-prometheus-1 | 0.5 | 1.69 | 67 |
| payflow-tempo-1 | 0.4 | 1.09 | 110 |
