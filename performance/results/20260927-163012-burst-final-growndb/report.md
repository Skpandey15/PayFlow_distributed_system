# Load-test report: 20260927-163012-burst-final

Window: 2026-09-27T11:00:16.620000+00:00 to 2026-09-27T11:06:36.697000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 16699 |
| iterations_per_s | 43.65642671072612 |
| dropped_iterations | 0 |
| payments_accepted | 16171 |
| payments_throttled | 517 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.9791448164582492 |
| post_p50_ms | 24.908734000000003 |
| post_p95_ms | 243.33520904999963 |
| post_p99_ms | 804.5200361100005 |
| post_max_ms | 5083.545538 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 42.8587 |
| api_5xx_ratio | 0.031 |
| api_post_p50_ms | 22.2 |
| api_post_p95_ms | 203.7 |
| api_post_p99_ms | 792.2 |
| api_get_p99_ms | 276.4 |
| saga_completed | 16379.1111 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 16379.1111} |
| saga_p50_ms | 58042.6 |
| saga_p95_ms | 106109.7 |
| saga_p99_ms | 112856.3 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 39843.3, "step=AWAITING_FUNDS": 42989.6, "step=AWAITING_RISK": 45879.0, "step=AWAITING_SETTLEMENT": 38806.9} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 10.0, "step=AWAITING_FUNDS": 12.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 10.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 2251.0, "step=AWAITING_FUNDS": 1514.0, "step=AWAITING_RISK": 2508.0, "step=AWAITING_SETTLEMENT": 1706.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 161.7 |
| outbox_publish_delay_p99_ms | 1142.9 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 448.7, "outbox=payment.outbox_event": 1635.6, "outbox=settlement.outbox_event": 592.4} |
| outbox_send_p99_ms | 57.3 |
| outbox_published_per_s | 472.3893 |
| outbox_backlog_max | {"outbox=account.outbox_event": 5.0, "outbox=payment.outbox_event": 19.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 2792.0, "group=payment-service,topic=fraud.events": 2501.0, "group=payment-service,topic=settlement.events": 1606.0, "group=fraud-service,topic=fraud.commands": 441.0, "group=account-service,topic=funds.commands": 432.0} |
| consumer_p99_ms | {"consumer=settlement-service": 136.3, "consumer=ledger-service": 53.0, "consumer=payment-service": 71.5, "consumer=account-service": 84.0, "consumer=fraud-service": 81.0} |
| events_consumed_per_s | 430.3088 |
| events_failed | {"category=CONCURRENCY": 2.0796} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 42.0 |
| hikari_acquire_max_ms | 3713.7 |
| hikari_acquire_avg_ms | 2.2 |
| hikari_usage_avg_ms | 11.3 |
| hikari_timeouts | 18.1276 |
| pg_commits_per_s | 1182.9493 |
| pg_rollbacks | 3.0444 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 338.0 |
| mongo_cmd_avg_ms | 1.6 |
| jvm_heap_used_max_mb | 222 |
| jvm_heap_after_gc_max_mb | 86 |
| jvm_heap_committed_max_mb | 237 |
| gc_pause_max_ms | 60.0 |
| gc_pause_total_ms | 2390.9 |
| gc_count | 501.3185 |
| alloc_rate_mb_s | 179.0 |
| threads_max | 74.0 |
| process_cpu_avg | 0.6724 |
| process_cpu_max | 0.9941 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 1.41 | 174 |
| payflow-kafka-1 | 29.0 | 77.2 | 824 |
| payflow-keycloak-1 | 0.5 | 3.46 | 3976 |
| payflow-mongo-1 | 25.0 | 91.13 | 401 |
| payflow-payflow-1 | 81.4 | 160.8 | 600 |
| payflow-postgres-1 | 49.2 | 107.41 | 625 |
| payflow-postgres-exporter-1 | 0.5 | 2.03 | 14 |
| payflow-prometheus-1 | 0.9 | 3.29 | 104 |
| payflow-settlement-rail-1 | 2.3 | 14.52 | 150 |
| payflow-tempo-1 | 8.5 | 73.39 | 1073 |
