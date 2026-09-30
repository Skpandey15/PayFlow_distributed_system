# Load-test report: 20260930-062505-stress-window300-wsl

Window: 2026-09-30T06:25:08.340000+00:00 to 2026-09-30T06:35:43.584000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 112920 |
| iterations_per_s | 177.19372922489634 |
| dropped_iterations | 179 |
| payments_accepted | 32954 |
| payments_throttled | 79964 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.38231227357346453 |
| post_p50_ms | 3.2778270000000003 |
| post_p95_ms | 51.820831649999995 |
| post_p99_ms | 112.36656120000005 |
| post_max_ms | 3663.565404 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 52.1391 |
| api_5xx_ratio | 0.7088 |
| api_post_p50_ms | 0.8 |
| api_post_p95_ms | 36.2 |
| api_post_p99_ms | 83.5 |
| api_get_p99_ms | 44.0 |
| saga_completed | 33202.2954 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 33202.2954} |
| saga_p50_ms | 5824.7 |
| saga_p95_ms | 12374.3 |
| saga_p99_ms | 25008.4 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 6166.8, "step=AWAITING_CAPTURE": 5687.4, "step=AWAITING_FUNDS": 5788.8, "step=AWAITING_SETTLEMENT": 5423.5} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 104.0, "step=AWAITING_FUNDS": 94.0, "step=AWAITING_RISK": 68.0, "step=AWAITING_SETTLEMENT": 70.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 136.0, "step=AWAITING_FUNDS": 120.0, "step=AWAITING_RISK": 130.0, "step=AWAITING_SETTLEMENT": 126.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 147.7 |
| outbox_publish_delay_p99_ms | 459.9 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 436.1, "outbox=payment.outbox_event": 445.6, "outbox=settlement.outbox_event": 535.3} |
| outbox_send_p99_ms | 63.2 |
| outbox_published_per_s | 571.2061 |
| outbox_backlog_max | {"outbox=account.outbox_event": 6.0, "outbox=payment.outbox_event": 141.0, "outbox=settlement.outbox_event": 5.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 2.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 216.0, "group=payment-service,topic=fraud.events": 164.0, "group=payment-service,topic=settlement.events": 105.0, "group=account-service,topic=funds.commands": 63.0, "group=settlement-service,topic=settlement.commands": 43.0} |
| consumer_p99_ms | {"consumer=ledger-service": 50.0, "consumer=payment-service": 54.2, "consumer=account-service": 65.1, "consumer=settlement-service": 101.9, "consumer=fraud-service": 78.7} |
| events_consumed_per_s | 519.2433 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 49.0 |
| hikari_acquire_max_ms | 2007.6 |
| hikari_acquire_avg_ms | 0.3 |
| hikari_usage_avg_ms | 9.2 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1439.4277 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 4503.0 |
| mongo_cmd_avg_ms | 1.8 |
| jvm_heap_used_max_mb | 237 |
| jvm_heap_after_gc_max_mb | 107 |
| jvm_heap_committed_max_mb | 275 |
| gc_pause_max_ms | 51.0 |
| gc_pause_total_ms | 5458.8 |
| gc_count | 1129.4463 |
| alloc_rate_mb_s | 235.9 |
| threads_max | 197.0 |
| process_cpu_avg | 0.8113 |
| process_cpu_max | 0.998 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 2.93 | 192 |
| payflow-kafka-1 | 62.0 | 177.74 | 597 |
| payflow-keycloak-1 | 3.0 | 78.33 | 1091 |
| payflow-mongo-1 | 17.7 | 80.04 | 292 |
| payflow-payflow-1 | 167.2 | 209.23 | 611 |
| payflow-postgres-1 | 74.3 | 147.54 | 465 |
| payflow-postgres-exporter-1 | 0.5 | 2.2 | 13 |
| payflow-prometheus-1 | 0.6 | 2.69 | 102 |
| payflow-settlement-rail-1 | 2.4 | 11.42 | 37 |
| payflow-tempo-1 | 2.3 | 12.95 | 112 |
