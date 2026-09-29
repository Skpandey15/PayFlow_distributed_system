# Load-test report: 20260929-212813-degraded-fault-F07-settlement-slow-bulkhead

Window: 2026-09-29T15:58:37.881000+00:00 to 2026-09-29T16:01:37.903000+00:00 (+78s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3512 |
| iterations_per_s | 18.72917421708623 |
| dropped_iterations | 89 |
| payments_accepted | 3510 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 26.158676999999997 |
| post_p95_ms | 653.4465302999993 |
| post_p99_ms | 2726.42735937 |
| post_max_ms | 5622.176488 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 19.4889 |
| api_5xx_ratio | None |
| api_post_p50_ms | 23.7 |
| api_post_p95_ms | 591.9 |
| api_post_p99_ms | 2547.4 |
| api_get_p99_ms | 802.3 |
| saga_completed | 3525.0769 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3525.0769} |
| saga_p50_ms | 72212.1 |
| saga_p95_ms | 154057.0 |
| saga_p99_ms | 164979.0 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 13826.9, "step=AWAITING_RISK": 15583.9, "step=AWAITING_SETTLEMENT": 155203.7, "step=AWAITING_CAPTURE": 12757.3} |
| drain_seconds_after_load | 78 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 104.0, "step=AWAITING_FUNDS": 128.0, "step=AWAITING_RISK": 280.0, "step=AWAITING_SETTLEMENT": 1837.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 327.0, "step=AWAITING_FUNDS": 128.0, "step=AWAITING_RISK": 280.0, "step=AWAITING_SETTLEMENT": 1837.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 227.5 |
| outbox_publish_delay_p99_ms | 7843.9 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 3826.6, "outbox=payment.outbox_event": 7983.0, "outbox=settlement.outbox_event": 7716.9} |
| outbox_send_p99_ms | 113.8 |
| outbox_published_per_s | 168.244 |
| outbox_backlog_max | {"outbox=account.outbox_event": 4.0, "outbox=payment.outbox_event": 283.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 5.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 425.0, "group=settlement-service,topic=settlement.commands": 365.0, "group=payment-service,topic=settlement.events": 274.0, "group=payment-service,topic=fraud.events": 171.0, "group=fraud-service,topic=fraud.commands": 111.0} |
| consumer_p99_ms | {"consumer=ledger-service": 105.4, "consumer=payment-service": 187.9, "consumer=account-service": 185.7, "consumer=fraud-service": 171.9, "consumer=settlement-service": 1797.4} |
| events_consumed_per_s | 147.1116 |
| events_failed | {"category=CONCURRENCY": 3.1533} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 31.0 |
| hikari_acquire_max_ms | 2731.8 |
| hikari_acquire_avg_ms | 3.0 |
| hikari_usage_avg_ms | 20.9 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 479.1543 |
| pg_rollbacks | 4.128 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 204.6 |
| mongo_cmd_avg_ms | 2.5 |
| jvm_heap_used_max_mb | 278 |
| jvm_heap_after_gc_max_mb | 106 |
| jvm_heap_committed_max_mb | 308 |
| gc_pause_max_ms | 83.0 |
| gc_pause_total_ms | 978.3 |
| gc_count | 94.2939 |
| alloc_rate_mb_s | 69.5 |
| threads_max | 196.0 |
| process_cpu_avg | 0.5912 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.3 | 3.12 | 285 |
| payflow-kafka-1 | 83.7 | 241.86 | 1069 |
| payflow-keycloak-1 | 0.6 | 6.32 | 1143 |
| payflow-mongo-1 | 25.5 | 80.66 | 359 |
| payflow-payflow-1 | 139.9 | 218.26 | 718 |
| payflow-postgres-1 | 65.9 | 221.02 | 528 |
| payflow-postgres-exporter-1 | 1.1 | 3.81 | 18 |
| payflow-prometheus-1 | 1.4 | 6.21 | 97 |
| payflow-settlement-rail-1 | 1.9 | 13.54 | 59 |
| payflow-tempo-1 | 3.0 | 21.72 | 114 |
