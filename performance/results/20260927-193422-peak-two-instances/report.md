# Load-test report: 20260927-193422-peak-two-instances

Window: 2026-09-27T14:04:25.610000+00:00 to 2026-09-27T14:13:25.669000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 24300 |
| iterations_per_s | 44.83213881584588 |
| dropped_iterations | 0 |
| payments_accepted | 24299 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 14.378485 |
| post_p95_ms | 55.2143605 |
| post_p99_ms | 186.43032730000004 |
| post_max_ms | 993.619131 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 45.2206 |
| api_5xx_ratio | None |
| api_post_p50_ms | 13.2 |
| api_post_p95_ms | 51.9 |
| api_post_p99_ms | 185.4 |
| api_get_p99_ms | 14.7 |
| saga_completed | 24427.302 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 24427.302} |
| saga_p50_ms | 1562.8 |
| saga_p95_ms | 2226.7 |
| saga_p99_ms | 4025.7 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 1188.8, "step=AWAITING_FUNDS": 1192.9, "step=AWAITING_RISK": 881.9, "step=AWAITING_SETTLEMENT": 1255.3} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 10.0, "step=AWAITING_FUNDS": 11.0, "step=AWAITING_RISK": 5.0, "step=AWAITING_SETTLEMENT": 13.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 49.0, "step=AWAITING_FUNDS": 64.0, "step=AWAITING_RISK": 40.0, "step=AWAITING_SETTLEMENT": 53.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 168.7 |
| outbox_publish_delay_p99_ms | 305.5 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=payment.outbox_event": 308.9, "outbox=settlement.outbox_event": 321.4, "outbox=account.outbox_event": 266.4} |
| outbox_send_p99_ms | 30.5 |
| outbox_published_per_s | 497.643 |
| outbox_backlog_max | {"outbox=account.outbox_event": 7.0, "outbox=payment.outbox_event": 29.0, "outbox=settlement.outbox_event": 7.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 64.0, "group=payment-service,topic=fraud.events": 34.0, "group=account-service,topic=funds.commands": 26.0, "group=payment-service,topic=settlement.events": 24.0, "group=ledger-service,topic=funds.events": 16.0} |
| consumer_p99_ms | {"consumer=fraud-service": 40.5, "consumer=settlement-service": 101.3, "consumer=ledger-service": 50.6, "consumer=payment-service": 55.5, "consumer=account-service": 65.8} |
| events_consumed_per_s | 452.1028 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 372.9 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 10.6 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1261.7215 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 43.0 |
| mongo_cmd_max_ms | 51.4 |
| mongo_cmd_avg_ms | 1.9 |
| jvm_heap_used_max_mb | 394 |
| jvm_heap_after_gc_max_mb | 149 |
| jvm_heap_committed_max_mb | 452 |
| gc_pause_max_ms | 29.0 |
| gc_pause_total_ms | 4103.3 |
| gc_count | 773.2835 |
| alloc_rate_mb_s | 185.7 |
| threads_max | 73.0 |
| process_cpu_avg | 0.4324 |
| process_cpu_max | 0.603 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 0.9 | 0.97 | 311 |
| payflow-kafka-1 | 24.2 | 27.99 | 503 |
| payflow-keycloak-1 | 0.4 | 0.48 | 716 |
| payflow-mongo-1 | 26.0 | 45.43 | 210 |
| payflow-payflow-1 | 91.0 | 93.86 | 525 |
| payflow-payflow-2 | 104.9 | 110.1 | 596 |
| payflow-postgres-1 | 57.7 | 65.37 | 180 |
| payflow-postgres-exporter-1 | 0.0 | 0.0 | 8 |
| payflow-prometheus-1 | 0.5 | 0.9 | 78 |
| payflow-settlement-rail-1 | 2.8 | 2.92 | 30 |
| payflow-tempo-1 | 1.7 | 2.77 | 132 |
