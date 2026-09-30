# Load-test report: 20260930-032633-degraded-fault-F07-settlement-slow-bulkhead

Window: 2026-09-30T03:32:09.478000+00:00 to 2026-09-30T03:35:18.183000+00:00 (+30s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3601 |
| iterations_per_s | 18.54558815307416 |
| dropped_iterations | 0 |
| payments_accepted | 3601 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 12.747216 |
| post_p95_ms | 129.943352 |
| post_p99_ms | 247.617148 |
| post_max_ms | 2180.125646 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 19.2126 |
| api_5xx_ratio | None |
| api_post_p50_ms | 11.6 |
| api_post_p95_ms | 99.4 |
| api_post_p99_ms | 171.5 |
| api_get_p99_ms | 59.4 |
| saga_completed | 3673.63 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3673.63} |
| saga_p50_ms | 1696.5 |
| saga_p95_ms | 8405.9 |
| saga_p99_ms | 13709.2 |
| saga_step_p99_ms | {"step=AWAITING_SETTLEMENT": 4336.7, "step=AWAITING_FUNDS": 3507.3, "step=AWAITING_RISK": 3668.4, "step=AWAITING_CAPTURE": 3722.1} |
| drain_seconds_after_load | 30 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 10.0, "step=AWAITING_FUNDS": 11.0, "step=AWAITING_RISK": 4.0, "step=AWAITING_SETTLEMENT": 10.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 44.0, "step=AWAITING_FUNDS": 46.0, "step=AWAITING_RISK": 32.0, "step=AWAITING_SETTLEMENT": 52.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 217.3 |
| outbox_publish_delay_p99_ms | 969.4 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=payment.outbox_event": 912.0, "outbox=account.outbox_event": 999.8, "outbox=settlement.outbox_event": 1216.5} |
| outbox_send_p99_ms | 85.2 |
| outbox_published_per_s | 211.7888 |
| outbox_backlog_max | {"outbox=account.outbox_event": 1.0, "outbox=payment.outbox_event": 15.0, "outbox=settlement.outbox_event": 4.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 59.0, "group=payment-service,topic=settlement.events": 44.0, "group=payment-service,topic=fraud.events": 37.0, "group=settlement-service,topic=settlement.commands": 10.0, "group=account-service,topic=funds.commands": 8.0} |
| consumer_p99_ms | {"consumer=ledger-service": 65.3, "consumer=payment-service": 79.9, "consumer=settlement-service": 176.0, "consumer=fraud-service": 140.0, "consumer=account-service": 90.0} |
| events_consumed_per_s | 192.5749 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 10.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 89.7 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 9.6 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 546.2533 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 91.1 |
| mongo_cmd_avg_ms | 2.3 |
| jvm_heap_used_max_mb | 277 |
| jvm_heap_after_gc_max_mb | 102 |
| jvm_heap_committed_max_mb | 317 |
| gc_pause_max_ms | 73.0 |
| gc_pause_total_ms | 792.7 |
| gc_count | 106.0976 |
| alloc_rate_mb_s | 76.7 |
| threads_max | 196.0 |
| process_cpu_avg | 0.5374 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.58 | 311 |
| payflow-kafka-1 | 58.7 | 185.06 | 674 |
| payflow-keycloak-1 | 0.7 | 7.51 | 572 |
| payflow-mongo-1 | 11.2 | 48.5 | 470 |
| payflow-payflow-1 | 109.3 | 203.38 | 731 |
| payflow-postgres-1 | 41.0 | 95.54 | 323 |
| payflow-postgres-exporter-1 | 0.3 | 1.81 | 9 |
| payflow-prometheus-1 | 0.5 | 1.92 | 56 |
| payflow-settlement-rail-1 | 2.6 | 16.64 | 49 |
| payflow-tempo-1 | 1.2 | 6.28 | 60 |
