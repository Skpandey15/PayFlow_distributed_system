# Load-test report: 20260929-142914-degraded-fault-F07-settlement-slow-bulkhead

Window: 2026-09-29T08:59:17.538000+00:00 to 2026-09-29T09:02:17.548000+00:00 (+312s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3600 |
| iterations_per_s | 19.844369594251603 |
| dropped_iterations | 0 |
| payments_accepted | 3600 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 10.6423865 |
| post_p95_ms | 27.037927799999995 |
| post_p99_ms | 38.581604 |
| post_max_ms | 52.849609 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 20.0 |
| api_5xx_ratio | 0.0 |
| api_post_p50_ms | 9.8 |
| api_post_p95_ms | 24.5 |
| api_post_p99_ms | 33.6 |
| api_get_p99_ms | 8.2 |
| saga_completed | 3637.7567 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3637.7567} |
| saga_p50_ms | 13396.2 |
| saga_p95_ms | 322341.1 |
| saga_p99_ms | 357671.3 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 600.1, "step=AWAITING_RISK": 356.5, "step=AWAITING_SETTLEMENT": 42086.9, "step=AWAITING_CAPTURE": 714.5} |
| drain_seconds_after_load | 312 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 10.0, "step=AWAITING_FUNDS": 9.0, "step=AWAITING_RISK": 4.0, "step=AWAITING_SETTLEMENT": 1382.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 29.0, "step=AWAITING_FUNDS": 12.0, "step=AWAITING_RISK": 8.0, "step=AWAITING_SETTLEMENT": 1790.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 207.8 |
| outbox_publish_delay_p99_ms | 334.9 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 245.8, "outbox=payment.outbox_event": 338.0, "outbox=settlement.outbox_event": 343.0} |
| outbox_send_p99_ms | 20.9 |
| outbox_published_per_s | 188.5543 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 12.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=settlement-service,topic=settlement.commands": 326.0, "group=settlement-service-settlement-service-retry-2,topic=settlement.commands-settlement-service-retry-2": 233.0, "group=settlement-service-settlement-service-retry-0,topic=settlement.commands-settlement-service-retry-0": 220.0, "group=settlement-service-settlement-service-retry-1,topic=settlement.commands-settlement-service-retry-1": 178.0, "group=payment-service,topic=funds.events": 57.0} |
| consumer_p99_ms | {"consumer=settlement-service": 1779.0, "consumer=ledger-service": 22.3, "consumer=payment-service": 26.6, "consumer=account-service": 30.2, "consumer=fraud-service": 37.6} |
| events_consumed_per_s | 160.7371 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 6713.6631, "category=CONCURRENCY": 0.0} |
| events_dead_lettered | 1388.1812 |
| hikari_active_max | 7.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 3.9 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 4.7 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 618.8229 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 42.0 |
| mongo_cmd_avg_ms | 1.5 |
| jvm_heap_used_max_mb | 335 |
| jvm_heap_after_gc_max_mb | 123 |
| jvm_heap_committed_max_mb | 364 |
| gc_pause_max_ms | 15.0 |
| gc_pause_total_ms | 542.7 |
| gc_count | 112.6021 |
| alloc_rate_mb_s | 105.0 |
| threads_max | 196.0 |
| process_cpu_avg | 0.4256 |
| process_cpu_max | 0.594 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 2.46 | 338 |
| payflow-kafka-1 | 60.5 | 147.18 | 1201 |
| payflow-keycloak-1 | 0.2 | 0.33 | 758 |
| payflow-mongo-1 | 11.4 | 54.78 | 354 |
| payflow-payflow-1 | 81.5 | 110.63 | 749 |
| payflow-postgres-1 | 38.5 | 68.35 | 514 |
| payflow-postgres-exporter-1 | 0.4 | 2.65 | 10 |
| payflow-prometheus-1 | 0.5 | 1.14 | 106 |
| payflow-settlement-rail-1 | 0.5 | 1.85 | 55 |
| payflow-tempo-1 | 1.0 | 5.93 | 154 |
