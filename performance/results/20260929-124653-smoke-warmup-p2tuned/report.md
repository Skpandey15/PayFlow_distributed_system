# Load-test report: 20260929-124653-smoke-warmup-p2tuned

Window: 2026-09-29T07:17:02.308000+00:00 to 2026-09-29T07:18:32.312000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 450 |
| iterations_per_s | 4.586737529451684 |
| dropped_iterations | 0 |
| payments_accepted | 450 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 14.621751 |
| post_p95_ms | 32.02764529999997 |
| post_p99_ms | 42.137122819999995 |
| post_max_ms | 105.353582 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 13.6 |
| api_post_p95_ms | 28.5 |
| api_post_p99_ms | 40.8 |
| api_get_p99_ms | 8.3 |
| saga_completed | 463.4893 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 463.4893} |
| saga_p50_ms | 1615.2 |
| saga_p95_ms | 1782.5 |
| saga_p99_ms | 2032.5 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 592.8, "step=AWAITING_FUNDS": 563.7, "step=AWAITING_RISK": 343.6, "step=AWAITING_SETTLEMENT": 624.6} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 2.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 226.9 |
| outbox_publish_delay_p99_ms | 321.3 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 242.4, "outbox=payment.outbox_event": 328.1, "outbox=settlement.outbox_event": 326.6} |
| outbox_send_p99_ms | 17.0 |
| outbox_published_per_s | 54.6316 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=settlement-service,topic=settlement.commands": 1.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=ledger-service": 21.6, "consumer=payment-service": 27.3, "consumer=account-service": 32.7, "consumer=fraud-service": 32.7, "consumer=settlement-service": 41.6} |
| events_consumed_per_s | 49.6547 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 4.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 19.5 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 7.0 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 158.8706 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 59.0 |
| mongo_cmd_avg_ms | 1.5 |
| jvm_heap_used_max_mb | 233 |
| jvm_heap_after_gc_max_mb | 101 |
| jvm_heap_committed_max_mb | 257 |
| gc_pause_max_ms | 46.0 |
| gc_pause_total_ms | 259.1 |
| gc_count | 21.0725 |
| alloc_rate_mb_s | 28.6 |
| threads_max | 193.0 |
| process_cpu_avg | 0.3686 |
| process_cpu_max | 0.5911 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 2.3 | 8.65 | 331 |
| payflow-kafka-1 | 65.9 | 190.17 | 604 |
| payflow-keycloak-1 | 0.3 | 0.74 | 689 |
| payflow-mongo-1 | 8.9 | 36.65 | 271 |
| payflow-payflow-1 | 66.3 | 91.16 | 615 |
| payflow-postgres-1 | 13.0 | 18.45 | 105 |
| payflow-postgres-exporter-1 | 0.4 | 1.8 | 8 |
| payflow-prometheus-1 | 0.6 | 1.6 | 69 |
| payflow-settlement-rail-1 | 1.4 | 2.44 | 29 |
| payflow-tempo-1 | 0.5 | 0.88 | 122 |
