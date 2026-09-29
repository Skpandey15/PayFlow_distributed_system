# Load-test report: 20260929-143917-degraded-fault-F08-settlement-timeout-unknown-outcome

Window: 2026-09-29T09:09:20.003000+00:00 to 2026-09-29T09:12:20.014000+00:00 (+30s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3600 |
| iterations_per_s | 19.827265961449985 |
| dropped_iterations | 0 |
| payments_accepted | 3600 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 12.064430000000002 |
| post_p95_ms | 28.82177105 |
| post_p99_ms | 39.640795719999986 |
| post_max_ms | 199.389011 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 20.0 |
| api_5xx_ratio | 0.0 |
| api_post_p50_ms | 10.9 |
| api_post_p95_ms | 25.9 |
| api_post_p99_ms | 35.1 |
| api_get_p99_ms | 8.4 |
| saga_completed | 3625.3171 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3625.3171} |
| saga_p50_ms | 9475.0 |
| saga_p95_ms | 65459.3 |
| saga_p99_ms | 82308.0 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 3454.0, "step=AWAITING_FUNDS": 2033.3, "step=AWAITING_RISK": 1771.8, "step=AWAITING_SETTLEMENT": 78153.1} |
| drain_seconds_after_load | 30 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 6.0, "step=AWAITING_FUNDS": 7.0, "step=AWAITING_RISK": 6.0, "step=AWAITING_SETTLEMENT": 12.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 312.0, "step=AWAITING_FUNDS": 26.0, "step=AWAITING_RISK": 30.0, "step=AWAITING_SETTLEMENT": 1242.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 211.3 |
| outbox_publish_delay_p99_ms | 348.4 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 319.8, "outbox=payment.outbox_event": 349.5, "outbox=settlement.outbox_event": 348.5} |
| outbox_send_p99_ms | 30.1 |
| outbox_published_per_s | 220.1029 |
| outbox_backlog_max | {"outbox=account.outbox_event": 3.0, "outbox=payment.outbox_event": 12.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=settlement-service,topic=settlement.commands": 1305.0, "group=payment-service,topic=funds.events": 273.0, "group=payment-service,topic=settlement.events": 214.0, "group=account-service,topic=funds.commands": 73.0, "group=payment-service,topic=fraud.events": 23.0} |
| consumer_p99_ms | {"consumer=fraud-service": 36.3, "consumer=settlement-service": 39.1, "consumer=ledger-service": 21.5, "consumer=payment-service": 25.6, "consumer=account-service": 27.8} |
| events_consumed_per_s | 200.0914 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 136.2439, "category=CONCURRENCY": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 8.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 8.5 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 5.7 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 567.3143 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 42.7 |
| mongo_cmd_avg_ms | 1.5 |
| jvm_heap_used_max_mb | 327 |
| jvm_heap_after_gc_max_mb | 126 |
| jvm_heap_committed_max_mb | 364 |
| gc_pause_max_ms | 10.0 |
| gc_pause_total_ms | 379.0 |
| gc_count | 77.8537 |
| alloc_rate_mb_s | 90.4 |
| threads_max | 194.0 |
| process_cpu_avg | 0.4109 |
| process_cpu_max | 0.974 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 1.75 | 329 |
| payflow-kafka-1 | 54.6 | 184.4 | 1130 |
| payflow-keycloak-1 | 0.2 | 0.31 | 757 |
| payflow-mongo-1 | 11.5 | 52.43 | 216 |
| payflow-payflow-1 | 74.7 | 108.75 | 754 |
| payflow-postgres-1 | 31.9 | 53.43 | 596 |
| payflow-postgres-exporter-1 | 0.5 | 2.82 | 12 |
| payflow-prometheus-1 | 0.6 | 1.21 | 79 |
| payflow-settlement-rail-1 | 0.6 | 1.51 | 66 |
| payflow-tempo-1 | 1.4 | 8.45 | 993 |
