# Load-test report: 20260929-144438-degraded-fault-F15-rebalance-extra-instance

Window: 2026-09-29T09:14:41.128000+00:00 to 2026-09-29T09:17:41.161000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3601 |
| iterations_per_s | 19.83885986584977 |
| dropped_iterations | 0 |
| payments_accepted | 3601 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 12.849608 |
| post_p95_ms | 34.299239 |
| post_p99_ms | 50.842226 |
| post_max_ms | 311.642842 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 20.0 |
| api_5xx_ratio | 0.0 |
| api_post_p50_ms | 11.9 |
| api_post_p95_ms | 31.9 |
| api_post_p99_ms | 48.0 |
| api_get_p99_ms | 9.4 |
| saga_completed | 3662.6774 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3662.6774} |
| saga_p50_ms | 1621.1 |
| saga_p95_ms | 6146.0 |
| saga_p99_ms | 9646.9 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 3103.6, "step=AWAITING_RISK": 2272.3, "step=AWAITING_SETTLEMENT": 3158.3, "step=AWAITING_CAPTURE": 3045.3} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 5.0, "step=AWAITING_FUNDS": 5.0, "step=AWAITING_RISK": 3.0, "step=AWAITING_SETTLEMENT": 8.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 27.0, "step=AWAITING_FUNDS": 36.0, "step=AWAITING_RISK": 22.0, "step=AWAITING_SETTLEMENT": 45.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 202.8 |
| outbox_publish_delay_p99_ms | 351.6 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 302.1, "outbox=payment.outbox_event": 352.7, "outbox=settlement.outbox_event": 352.4} |
| outbox_send_p99_ms | 74.8 |
| outbox_published_per_s | 219.5095 |
| outbox_backlog_max | {"outbox=account.outbox_event": 14.0, "outbox=payment.outbox_event": 21.0, "outbox=settlement.outbox_event": 8.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=ledger-service,topic=funds.events": 47.0, "group=payment-service,topic=settlement.events": 44.0, "group=payment-service,topic=funds.events": 41.0, "group=account-service,topic=funds.commands": 30.0, "group=fraud-service,topic=fraud.commands": 23.0} |
| consumer_p99_ms | {"consumer=settlement-service": 285.7, "consumer=ledger-service": 109.6, "consumer=payment-service": 166.2, "consumer=account-service": 195.0, "consumer=fraud-service": 271.8} |
| events_consumed_per_s | 199.0222 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 0.0, "category=CONCURRENCY": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 9.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 93.3 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 12.4 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 570.9543 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 43.0 |
| mongo_cmd_max_ms | 425.3 |
| mongo_cmd_avg_ms | 3.2 |
| jvm_heap_used_max_mb | 552 |
| jvm_heap_after_gc_max_mb | 235 |
| jvm_heap_committed_max_mb | 648 |
| gc_pause_max_ms | 92.0 |
| gc_pause_total_ms | 1181.8 |
| gc_count | 108.7583 |
| alloc_rate_mb_s | 105.1 |
| threads_max | 348.0 |
| process_cpu_avg | 0.5451 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 1.6 | 320 |
| payflow-kafka-1 | 61.1 | 184.72 | 1152 |
| payflow-keycloak-1 | 1.1 | 13.04 | 755 |
| payflow-mongo-1 | 14.4 | 50.63 | 370 |
| payflow-payflow-1 | 70.7 | 110.73 | 758 |
| payflow-payflow-2 | 154.1 | 205.69 | 656 |
| payflow-postgres-1 | 40.9 | 54.31 | 655 |
| payflow-postgres-exporter-1 | 0.5 | 2.39 | 12 |
| payflow-prometheus-1 | 0.8 | 1.94 | 84 |
| payflow-settlement-rail-1 | 1.1 | 2.96 | 61 |
| payflow-tempo-1 | 1.6 | 6.27 | 144 |
