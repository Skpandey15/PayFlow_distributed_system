# Load-test report: 20260929-111704-degraded-fault-F14-consumer-crash-sigkill

Window: 2026-09-29T05:47:07.244000+00:00 to 2026-09-29T05:50:07.391000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3405 |
| iterations_per_s | 18.751460027399084 |
| dropped_iterations | 196 |
| payments_accepted | 2976 |
| payments_throttled | 0 |
| payments_rejected_at_api | 429 |
| checks_pass_rate | 0.9127871518601341 |
| post_p50_ms | 19.615703 |
| post_p95_ms | 322.3631927999994 |
| post_p99_ms | 834.3705476000007 |
| post_max_ms | 2945.956283 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 15.88 |
| api_5xx_ratio | 0.0 |
| api_post_p50_ms | 25.7 |
| api_post_p95_ms | 211.4 |
| api_post_p99_ms | 595.1 |
| api_get_p99_ms | 162.6 |
| saga_completed | 2942.678 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 2942.678} |
| saga_p50_ms | 19632.0 |
| saga_p95_ms | 48514.7 |
| saga_p99_ms | 55905.9 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 13999.4, "step=AWAITING_RISK": 29476.0, "step=AWAITING_SETTLEMENT": 10540.4, "step=AWAITING_CAPTURE": 13880.0} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 187.0, "step=AWAITING_FUNDS": 99.0, "step=AWAITING_RISK": 51.0, "step=AWAITING_SETTLEMENT": 110.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 307.0, "step=AWAITING_FUNDS": 336.0, "step=AWAITING_RISK": 368.0, "step=AWAITING_SETTLEMENT": 153.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 214.3 |
| outbox_publish_delay_p99_ms | 2561.0 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 1261.4, "outbox=payment.outbox_event": 4182.2, "outbox=settlement.outbox_event": 1109.8} |
| outbox_send_p99_ms | 208.6 |
| outbox_published_per_s | 165.4343 |
| outbox_backlog_max | {"outbox=account.outbox_event": 11.0, "outbox=payment.outbox_event": 45.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 437.0, "group=fraud-service,topic=fraud.commands": 300.0, "group=payment-service,topic=fraud.events": 269.0, "group=payment-service,topic=settlement.events": 190.0, "group=account-service,topic=funds.commands": 41.0} |
| consumer_p99_ms | {"consumer=ledger-service": 89.3, "consumer=payment-service": 105.2, "consumer=account-service": 133.1, "consumer=fraud-service": 335.4, "consumer=settlement-service": 292.9} |
| events_consumed_per_s | 149.3029 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 0.0, "category=UNKNOWN": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 12.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 623.1 |
| hikari_acquire_avg_ms | 0.2 |
| hikari_usage_avg_ms | 17.6 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 432.5371 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 318.1 |
| mongo_cmd_avg_ms | 3.8 |
| jvm_heap_used_max_mb | 217 |
| jvm_heap_after_gc_max_mb | 81 |
| jvm_heap_committed_max_mb | 234 |
| gc_pause_max_ms | 61.0 |
| gc_pause_total_ms | 1119.8 |
| gc_count | 114.2488 |
| alloc_rate_mb_s | 65.5 |
| threads_max | 72.0 |
| process_cpu_avg | 0.7249 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.3 | 2.79 | 325 |
| payflow-kafka-1 | 60.5 | 191.99 | 686 |
| payflow-keycloak-1 | 6.0 | 46.1 | 776 |
| payflow-mongo-1 | 10.1 | 47.53 | 390 |
| payflow-payflow-1 | 159.6 | 210.1 | 592 |
| payflow-postgres-1 | 31.8 | 75.64 | 743 |
| payflow-postgres-exporter-1 | 0.3 | 2.42 | 10 |
| payflow-prometheus-1 | 0.5 | 0.88 | 76 |
| payflow-settlement-rail-1 | 0.7 | 3.09 | 43 |
| payflow-tempo-1 | 1.1 | 5.93 | 164 |
