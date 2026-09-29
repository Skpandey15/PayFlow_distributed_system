# Load-test report: 20260927-145943-stress-g1-limit48

Window: 2026-09-27T09:29:51.297000+00:00 to 2026-09-27T09:39:51.428000+00:00 (+1377s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 112929 |
| iterations_per_s | 186.21336598769685 |
| dropped_iterations | 171 |
| payments_accepted | 106918 |
| payments_throttled | 6009 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.9639694198770798 |
| post_p50_ms | 38.193552 |
| post_p95_ms | 187.34814629999994 |
| post_p99_ms | 466.73706776000006 |
| post_max_ms | 2473.202756 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 179.3912 |
| api_5xx_ratio | 0.0529 |
| api_post_p50_ms | 29.7 |
| api_post_p95_ms | 150.1 |
| api_post_p99_ms | 441.7 |
| api_get_p99_ms | 192.2 |
| saga_completed | 107278.8442 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 107278.8442} |
| saga_p50_ms | 600000.0 |
| saga_p95_ms | 600000.0 |
| saga_p99_ms | 600000.0 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 600000.0, "step=AWAITING_FUNDS": 600000.0, "step=AWAITING_RISK": 600000.0, "step=AWAITING_SETTLEMENT": 227423.0} |
| drain_seconds_after_load | 1377 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 4901.0, "step=AWAITING_FUNDS": 13159.0, "step=AWAITING_RISK": 74164.0, "step=AWAITING_SETTLEMENT": 3638.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 36332.0, "step=AWAITING_FUNDS": 39267.0, "step=AWAITING_RISK": 75085.0, "step=AWAITING_SETTLEMENT": 5159.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 154.1 |
| outbox_publish_delay_p99_ms | 1065.5 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 866.2, "outbox=payment.outbox_event": 1233.7, "outbox=settlement.outbox_event": 734.1} |
| outbox_send_p99_ms | 73.1 |
| outbox_published_per_s | 678.7244 |
| outbox_backlog_max | {"outbox=account.outbox_event": 12.0, "outbox=payment.outbox_event": 255.0, "outbox=settlement.outbox_event": 5.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 1.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 62096.0, "group=payment-service,topic=fraud.events": 59997.0, "group=fraud-service,topic=fraud.commands": 41292.0, "group=payment-service,topic=settlement.events": 4726.0, "group=account-service,topic=funds.commands": 1501.0} |
| consumer_p99_ms | {"consumer=ledger-service": 44.6, "consumer=payment-service": 57.9, "consumer=account-service": 68.4, "consumer=fraud-service": 86.6, "consumer=settlement-service": 111.5} |
| events_consumed_per_s | 449.8198 |
| events_failed | {"category=CONCURRENCY": 34.1486, "category=TRANSIENT_INFRASTRUCTURE": 2.0102} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 52.0 |
| hikari_acquire_max_ms | 1479.5 |
| hikari_acquire_avg_ms | 1.9 |
| hikari_usage_avg_ms | 9.6 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 2258.879 |
| pg_rollbacks | 37.0375 |
| pg_deadlocks | 0 |
| pg_backends_max | 25.0 |
| mongo_cmd_max_ms | 5423.1 |
| mongo_cmd_avg_ms | 1.6 |
| jvm_heap_used_max_mb | 259 |
| jvm_heap_after_gc_max_mb | 126 |
| jvm_heap_committed_max_mb | 276 |
| gc_pause_max_ms | 71.0 |
| gc_pause_total_ms | 14596.7 |
| gc_count | 2754.7538 |
| alloc_rate_mb_s | 229.5 |
| threads_max | 74.0 |
| process_cpu_avg | 0.8633 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 1.58 | 187 |
| payflow-kafka-1 | 64.6 | 196.84 | 952 |
| payflow-keycloak-1 | 0.2 | 0.46 | 2971 |
| payflow-mongo-1 | 26.5 | 88.08 | 414 |
| payflow-payflow-1 | 161.4 | 205.71 | 615 |
| payflow-postgres-1 | 77.1 | 145.71 | 708 |
| payflow-postgres-exporter-1 | 0.3 | 2.14 | 13 |
| payflow-prometheus-1 | 0.6 | 1.97 | 88 |
| payflow-settlement-rail-1 | 1.4 | 2.75 | 66 |
