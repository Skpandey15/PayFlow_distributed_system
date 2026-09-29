# Load-test report: 20260927-121018-smoke-warmup-baseline

Window: 2026-09-27T06:40:23.510000+00:00 to 2026-09-27T06:41:53.512000+00:00 (+30s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 450 |
| iterations_per_s | 4.789315409024428 |
| dropped_iterations | 0 |
| payments_accepted | 450 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 12.880389000000001 |
| post_p95_ms | 26.468480149999998 |
| post_p99_ms | 39.70538359999997 |
| post_max_ms | 196.874095 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 4.7881 |
| api_5xx_ratio | None |
| api_post_p50_ms | 11.7 |
| api_post_p95_ms | 24.1 |
| api_post_p99_ms | 35.4 |
| api_get_p99_ms | 8.0 |
| saga_completed | 452.0653 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 452.0653} |
| saga_p50_ms | 1706.1 |
| saga_p95_ms | 2117.9 |
| saga_p99_ms | 2335.2 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 677.5, "step=AWAITING_FUNDS": 787.0, "step=AWAITING_RISK": 441.8, "step=AWAITING_SETTLEMENT": 622.1} |
| drain_seconds_after_load | 30 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 2.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 4.0, "step=AWAITING_FUNDS": 4.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 2.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 259.7 |
| outbox_publish_delay_p99_ms | 429.8 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 357.0, "outbox=payment.outbox_event": 433.6, "outbox=settlement.outbox_event": 428.1} |
| outbox_send_p99_ms | 12.5 |
| outbox_published_per_s | 52.7883 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 9.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 0.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=payment-service": 21.5, "consumer=account-service": 19.1, "consumer=fraud-service": 23.9, "consumer=settlement-service": 16.3, "consumer=ledger-service": 17.2} |
| events_consumed_per_s | 47.9763 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 1.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 6.1 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 8.6 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 153.6 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 89.5 |
| mongo_cmd_avg_ms | 0.7 |
| jvm_heap_used_max_mb | 146 |
| jvm_heap_after_gc_max_mb | 91 |
| jvm_heap_committed_max_mb | 211 |
| gc_pause_max_ms | 205.0 |
| gc_pause_total_ms | 282.0 |
| gc_count | 53.8676 |
| alloc_rate_mb_s | 30.7 |
| threads_max | 187.0 |
| process_cpu_avg | 0.355 |
| process_cpu_max | 0.586 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 1.8 | 320 |
| payflow-kafka-1 | 45.6 | 160.25 | 584 |
| payflow-keycloak-1 | 0.5 | 2.0 | 704 |
| payflow-mongo-1 | 7.2 | 54.22 | 350 |
| payflow-payflow-1 | 64.9 | 111.65 | 519 |
| payflow-postgres-1 | 11.5 | 18.37 | 101 |
| payflow-postgres-exporter-1 | 0.6 | 2.66 | 9 |
| payflow-prometheus-1 | 0.5 | 0.88 | 85 |
| payflow-tempo-1 | 0.5 | 1.12 | 804 |
