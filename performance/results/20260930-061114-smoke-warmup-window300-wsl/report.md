# Load-test report: 20260930-061114-smoke-warmup-window300-wsl

Window: 2026-09-30T06:11:21.568000+00:00 to 2026-09-30T06:12:55.747000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.472011075260229 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 14.815389 |
| post_p95_ms | 29.4669095 |
| post_p99_ms | 48.029987000000006 |
| post_max_ms | 96.109205 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 4.7785 |
| api_5xx_ratio | None |
| api_post_p50_ms | 13.9 |
| api_post_p95_ms | 27.1 |
| api_post_p99_ms | 38.8 |
| api_get_p99_ms | 9.3 |
| saga_completed | 451.6166 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 451.6166} |
| saga_p50_ms | 1623.0 |
| saga_p95_ms | 2009.6 |
| saga_p99_ms | 3817.6 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 348.6, "step=AWAITING_CAPTURE": 622.1, "step=AWAITING_FUNDS": 2556.2, "step=AWAITING_SETTLEMENT": 2553.4} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 2.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 226.0 |
| outbox_publish_delay_p99_ms | 343.4 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=settlement.outbox_event": 345.7, "outbox=account.outbox_event": 275.1, "outbox=payment.outbox_event": 346.7} |
| outbox_send_p99_ms | 25.4 |
| outbox_published_per_s | 52.2565 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=settlement-service,topic=settlement.commands": 1.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=account-service,topic=funds.commands": 0.0} |
| consumer_p99_ms | {"consumer=fraud-service": 44.5, "consumer=ledger-service": 19.9, "consumer=payment-service": 29.9, "consumer=account-service": 35.8, "consumer=settlement-service": 47.7} |
| events_consumed_per_s | 47.5257 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 3.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 328.8 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 6.0 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 162.522 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 57.2 |
| mongo_cmd_avg_ms | 1.5 |
| jvm_heap_used_max_mb | 214 |
| jvm_heap_after_gc_max_mb | 98 |
| jvm_heap_committed_max_mb | 229 |
| gc_pause_max_ms | 53.0 |
| gc_pause_total_ms | 275.2 |
| gc_count | 26.9694 |
| alloc_rate_mb_s | 29.6 |
| threads_max | 194.0 |
| process_cpu_avg | 0.3581 |
| process_cpu_max | 0.5934 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.28 | 172 |
| payflow-kafka-1 | 45.8 | 179.88 | 614 |
| payflow-keycloak-1 | 0.2 | 0.28 | 693 |
| payflow-mongo-1 | 11.4 | 47.71 | 178 |
| payflow-payflow-1 | 63.4 | 86.65 | 598 |
| payflow-postgres-1 | 12.0 | 15.27 | 110 |
| payflow-postgres-exporter-1 | 0.2 | 1.45 | 15 |
| payflow-prometheus-1 | 0.7 | 1.92 | 113 |
| payflow-settlement-rail-1 | 2.5 | 9.08 | 40 |
| payflow-tempo-1 | 0.7 | 1.94 | 100 |
