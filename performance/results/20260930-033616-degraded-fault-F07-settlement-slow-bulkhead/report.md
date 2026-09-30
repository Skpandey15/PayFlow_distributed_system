# Load-test report: 20260930-033616-degraded-fault-F07-settlement-slow-bulkhead

Window: 2026-09-30T03:36:17.943000+00:00 to 2026-09-30T03:39:29.149000+00:00 (+301s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3600 |
| iterations_per_s | 18.69237382534395 |
| dropped_iterations | 0 |
| payments_accepted | 3600 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 9.571017000000001 |
| post_p95_ms | 18.198449 |
| post_p99_ms | 30.906792339999992 |
| post_max_ms | 316.851992 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 18.7975 |
| api_5xx_ratio | None |
| api_post_p50_ms | 9.0 |
| api_post_p95_ms | 17.2 |
| api_post_p99_ms | 28.0 |
| api_get_p99_ms | 5.3 |
| saga_completed | 3596.9106 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3596.9106} |
| saga_p50_ms | 79333.2 |
| saga_p95_ms | 303725.4 |
| saga_p99_ms | 363320.0 |
| saga_step_p99_ms | {"step=AWAITING_SETTLEMENT": 174761.8, "step=AWAITING_FUNDS": 2655.5, "step=AWAITING_RISK": 9418.2, "step=AWAITING_CAPTURE": 2264.9} |
| drain_seconds_after_load | 301 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 20.0, "step=AWAITING_FUNDS": 10.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 1796.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 20.0, "step=AWAITING_FUNDS": 28.0, "step=AWAITING_RISK": 207.0, "step=AWAITING_SETTLEMENT": 1796.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 210.3 |
| outbox_publish_delay_p99_ms | 3837.7 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=payment.outbox_event": 5008.7, "outbox=account.outbox_event": 313.0, "outbox=settlement.outbox_event": 344.8} |
| outbox_send_p99_ms | 13.2 |
| outbox_published_per_s | 171.3687 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 15.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=settlement-service,topic=settlement.commands": 320.0, "group=fraud-service,topic=fraud.commands": 198.0, "group=account-service,topic=funds.commands": 8.0, "group=payment-service,topic=fraud.events": 6.0, "group=payment-service,topic=settlement.events": 5.0} |
| consumer_p99_ms | {"consumer=account-service": 32.0, "consumer=ledger-service": 20.2, "consumer=payment-service": 27.5, "consumer=settlement-service": 88.5, "consumer=fraud-service": 31.3} |
| events_consumed_per_s | 152.5873 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 7.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 4.7 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 4.0 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 470.9983 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 44.6 |
| mongo_cmd_avg_ms | 1.0 |
| jvm_heap_used_max_mb | 283 |
| jvm_heap_after_gc_max_mb | 100 |
| jvm_heap_committed_max_mb | 317 |
| gc_pause_max_ms | 16.0 |
| gc_pause_total_ms | 579.8 |
| gc_count | 115.1496 |
| alloc_rate_mb_s | 62.8 |
| threads_max | 195.0 |
| process_cpu_avg | 0.3096 |
| process_cpu_max | 0.437 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 0.9 | 1.39 | 311 |
| payflow-kafka-1 | 46.2 | 189.44 | 730 |
| payflow-keycloak-1 | 0.2 | 0.19 | 841 |
| payflow-mongo-1 | 7.6 | 45.45 | 373 |
| payflow-payflow-1 | 69.7 | 149.04 | 721 |
| payflow-postgres-1 | 27.7 | 71.25 | 325 |
| payflow-postgres-exporter-1 | 0.3 | 1.39 | 9 |
| payflow-prometheus-1 | 0.5 | 1.37 | 62 |
| payflow-settlement-rail-1 | 0.6 | 2.66 | 42 |
| payflow-tempo-1 | 1.1 | 4.15 | 60 |
