# Load-test report: 20260927-165245-smoke-warmup-serial

Window: 2026-09-27T11:23:06.930000+00:00 to 2026-09-27T11:24:36.959000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.78473013158985 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 23.906802 |
| post_p95_ms | 56.895455 |
| post_p99_ms | 94.9790105 |
| post_max_ms | 181.86542 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 22.8 |
| api_post_p95_ms | 54.7 |
| api_post_p99_ms | 94.4 |
| api_get_p99_ms | 11.2 |
| saga_completed | 463.1233 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 463.1233} |
| saga_p50_ms | 1640.9 |
| saga_p95_ms | 2135.0 |
| saga_p99_ms | 4364.3 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 670.3, "step=AWAITING_FUNDS": 742.7, "step=AWAITING_RISK": 2081.3, "step=AWAITING_SETTLEMENT": 820.0} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 4.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 1.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 4.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 234.0 |
| outbox_publish_delay_p99_ms | 378.2 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 332.6, "outbox=payment.outbox_event": 398.8, "outbox=settlement.outbox_event": 384.6} |
| outbox_send_p99_ms | 20.6 |
| outbox_published_per_s | 54.5888 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 2.0, "group=payment-service,topic=settlement.events": 1.0, "group=payment-service,topic=fraud.events": 0.0, "group=payment-service,topic=funds.events": 0.0, "group=ledger-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=settlement-service": 82.4, "consumer=ledger-service": 47.8, "consumer=payment-service": 51.4, "consumer=account-service": 57.1, "consumer=fraud-service": 42.2} |
| events_consumed_per_s | 49.6337 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 4.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 7.0 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 11.1 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 158.8235 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 54.6 |
| mongo_cmd_avg_ms | 1.5 |
| jvm_heap_used_max_mb | 121 |
| jvm_heap_after_gc_max_mb | 73 |
| jvm_heap_committed_max_mb | 173 |
| gc_pause_max_ms | 157.0 |
| gc_pause_total_ms | 242.0 |
| gc_count | 61.0261 |
| alloc_rate_mb_s | 28.7 |
| threads_max | 71.0 |
| process_cpu_avg | 0.3488 |
| process_cpu_max | 0.7122 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 1.63 | 185 |
| payflow-kafka-1 | 54.6 | 178.3 | 807 |
| payflow-keycloak-1 | 0.5 | 1.7 | 4505 |
| payflow-mongo-1 | 4.3 | 13.99 | 228 |
| payflow-payflow-1 | 91.8 | 124.03 | 493 |
| payflow-postgres-1 | 15.5 | 20.55 | 516 |
| payflow-postgres-exporter-1 | 0.3 | 1.63 | 14 |
| payflow-prometheus-1 | 0.5 | 0.77 | 87 |
| payflow-settlement-rail-1 | 0.2 | 0.41 | 143 |
| payflow-tempo-1 | 0.6 | 1.81 | 92 |
