# Load-test report: 20260930-034525-degraded-fault-F07-settlement-slow-bulkhead

Window: 2026-09-30T03:45:31.008000+00:00 to 2026-09-30T03:48:38.765000+00:00 (+142s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3601 |
| iterations_per_s | 18.692563427287613 |
| dropped_iterations | 0 |
| payments_accepted | 3601 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 13.812569 |
| post_p95_ms | 170.449899 |
| post_p99_ms | 284.163605 |
| post_max_ms | 1616.601252 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 19.1455 |
| api_5xx_ratio | None |
| api_post_p50_ms | 12.9 |
| api_post_p95_ms | 106.1 |
| api_post_p99_ms | 183.1 |
| api_get_p99_ms | 67.8 |
| saga_completed | 3602.2545 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3602.2545} |
| saga_p50_ms | 22517.7 |
| saga_p95_ms | 164193.2 |
| saga_p99_ms | 201894.1 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 6411.1, "step=AWAITING_SETTLEMENT": 135446.0, "step=AWAITING_FUNDS": 5715.5, "step=AWAITING_RISK": 5201.5} |
| drain_seconds_after_load | 142 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 20.0, "step=AWAITING_FUNDS": 8.0, "step=AWAITING_RISK": 4.0, "step=AWAITING_SETTLEMENT": 1380.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 72.0, "step=AWAITING_FUNDS": 77.0, "step=AWAITING_RISK": 65.0, "step=AWAITING_SETTLEMENT": 1651.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 209.3 |
| outbox_publish_delay_p99_ms | 1526.2 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=payment.outbox_event": 1672.6, "outbox=account.outbox_event": 867.8, "outbox=settlement.outbox_event": 923.8} |
| outbox_send_p99_ms | 94.7 |
| outbox_published_per_s | 183.1599 |
| outbox_backlog_max | {"outbox=account.outbox_event": 5.0, "outbox=payment.outbox_event": 22.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=settlement-service,topic=settlement.commands": 315.0, "group=payment-service,topic=funds.events": 127.0, "group=payment-service,topic=settlement.events": 71.0, "group=payment-service,topic=fraud.events": 67.0, "group=fraud-service,topic=fraud.commands": 65.0} |
| consumer_p99_ms | {"consumer=account-service": 104.6, "consumer=ledger-service": 75.9, "consumer=payment-service": 86.3, "consumer=settlement-service": 1604.1, "consumer=fraud-service": 189.4} |
| events_consumed_per_s | 163.949 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 12.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 104.2 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 8.8 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 504.5684 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 87.7 |
| mongo_cmd_avg_ms | 2.3 |
| jvm_heap_used_max_mb | 182 |
| jvm_heap_after_gc_max_mb | 94 |
| jvm_heap_committed_max_mb | 197 |
| gc_pause_max_ms | 70.0 |
| gc_pause_total_ms | 1275.4 |
| gc_count | 217.0704 |
| alloc_rate_mb_s | 78.2 |
| threads_max | 194.0 |
| process_cpu_avg | 0.5864 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.95 | 307 |
| payflow-kafka-1 | 49.2 | 175.52 | 779 |
| payflow-keycloak-1 | 0.2 | 0.69 | 1197 |
| payflow-mongo-1 | 12.1 | 53.85 | 470 |
| payflow-payflow-1 | 112.0 | 201.4 | 569 |
| payflow-postgres-1 | 27.2 | 48.01 | 354 |
| payflow-postgres-exporter-1 | 0.3 | 1.58 | 11 |
| payflow-prometheus-1 | 0.5 | 1.43 | 79 |
| payflow-settlement-rail-1 | 0.6 | 1.41 | 49 |
| payflow-tempo-1 | 1.1 | 4.86 | 75 |
