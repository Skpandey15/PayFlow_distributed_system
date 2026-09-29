# Load-test report: 20260927-183901-stress-final

Window: 2026-09-27T13:09:06.164000+00:00 to 2026-09-27T13:19:06.287000+00:00 (+94s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 113095 |
| iterations_per_s | 187.26556679810332 |
| dropped_iterations | 4 |
| payments_accepted | 38689 |
| payments_throttled | 74406 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.4384664729632844 |
| post_p50_ms | 2.569026 |
| post_p95_ms | 94.15523479999987 |
| post_p99_ms | 252.49582693999997 |
| post_max_ms | 1364.896625 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 64.9563 |
| api_5xx_ratio | 0.657 |
| api_post_p50_ms | 0.9 |
| api_post_p95_ms | 80.0 |
| api_post_p99_ms | 234.5 |
| api_get_p99_ms | 206.0 |
| saga_completed | 38892.1623 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 38892.1623} |
| saga_p50_ms | 128771.6 |
| saga_p95_ms | 212512.5 |
| saga_p99_ms | 251842.5 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 96964.0, "step=AWAITING_RISK": 76943.0, "step=AWAITING_SETTLEMENT": 46186.7, "step=AWAITING_CAPTURE": 106048.3} |
| drain_seconds_after_load | 94 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 1867.0, "step=AWAITING_FUNDS": 4734.0, "step=AWAITING_RISK": 2753.0, "step=AWAITING_SETTLEMENT": 122.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 6117.0, "step=AWAITING_FUNDS": 7414.0, "step=AWAITING_RISK": 7841.0, "step=AWAITING_SETTLEMENT": 5581.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 147.2 |
| outbox_publish_delay_p99_ms | 511.6 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 439.2, "outbox=payment.outbox_event": 531.5, "outbox=settlement.outbox_event": 445.5} |
| outbox_send_p99_ms | 66.0 |
| outbox_published_per_s | 628.8924 |
| outbox_backlog_max | {"outbox=account.outbox_event": 9.0, "outbox=payment.outbox_event": 155.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 9571.0, "group=payment-service,topic=fraud.events": 6747.0, "group=payment-service,topic=settlement.events": 4161.0, "group=fraud-service,topic=fraud.commands": 2751.0, "group=account-service,topic=funds.commands": 979.0} |
| consumer_p99_ms | {"consumer=payment-service": 56.8, "consumer=account-service": 67.6, "consumer=fraud-service": 80.2, "consumer=settlement-service": 92.2, "consumer=ledger-service": 45.4} |
| events_consumed_per_s | 574.685 |
| events_failed | {"category=CONCURRENCY": 19.1218} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 48.0 |
| hikari_acquire_max_ms | 1287.1 |
| hikari_acquire_avg_ms | 1.4 |
| hikari_usage_avg_ms | 8.9 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1604.079 |
| pg_rollbacks | 21.0782 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 112.9 |
| mongo_cmd_avg_ms | 1.3 |
| jvm_heap_used_max_mb | 275 |
| jvm_heap_after_gc_max_mb | 105 |
| jvm_heap_committed_max_mb | 299 |
| gc_pause_max_ms | 47.0 |
| gc_pause_total_ms | 5100.3 |
| gc_count | 1179.7664 |
| alloc_rate_mb_s | 254.9 |
| threads_max | 73.0 |
| process_cpu_avg | 0.8546 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.18 | 333 |
| payflow-kafka-1 | 66.4 | 113.05 | 482 |
| payflow-keycloak-1 | 0.2 | 0.32 | 707 |
| payflow-mongo-1 | 4.2 | 4.9 | 207 |
| payflow-payflow-1 | 91.9 | 92.72 | 580 |
| payflow-postgres-1 | 36.5 | 43.72 | 125 |
| payflow-postgres-exporter-1 | 0.6 | 1.28 | 9 |
| payflow-prometheus-1 | 0.4 | 0.43 | 63 |
| payflow-settlement-rail-1 | 3.6 | 4.39 | 32 |
| payflow-tempo-1 | 0.6 | 0.9 | 91 |
