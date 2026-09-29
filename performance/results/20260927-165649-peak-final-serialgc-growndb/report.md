# Load-test report: 20260927-165649-peak-final-serialgc

Window: 2026-09-27T11:26:52.821000+00:00 to 2026-09-27T11:35:52.868000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 24170 |
| iterations_per_s | 44.57985172800831 |
| dropped_iterations | 129 |
| payments_accepted | 24036 |
| payments_throttled | 126 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.9965244255647808 |
| post_p50_ms | 16.823386499999998 |
| post_p95_ms | 91.16670055000002 |
| post_p99_ms | 813.8945008199983 |
| post_max_ms | 2436.173064 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 44.7377 |
| api_5xx_ratio | 0.0051 |
| api_post_p50_ms | 14.7 |
| api_post_p95_ms | 84.6 |
| api_post_p99_ms | 763.2 |
| api_get_p99_ms | 44.3 |
| saga_completed | 24284.1752 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 24284.1752} |
| saga_p50_ms | 8745.2 |
| saga_p95_ms | 51164.4 |
| saga_p99_ms | 60811.1 |
| saga_step_p99_ms | {"step=AWAITING_SETTLEMENT": 22925.5, "step=AWAITING_CAPTURE": 17828.8, "step=AWAITING_FUNDS": 19142.3, "step=AWAITING_RISK": 25819.8} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 17.0, "step=AWAITING_FUNDS": 14.0, "step=AWAITING_RISK": 5.0, "step=AWAITING_SETTLEMENT": 12.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 906.0, "step=AWAITING_FUNDS": 645.0, "step=AWAITING_RISK": 1012.0, "step=AWAITING_SETTLEMENT": 774.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 176.3 |
| outbox_publish_delay_p99_ms | 685.1 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 442.2, "outbox=payment.outbox_event": 831.4, "outbox=settlement.outbox_event": 513.1} |
| outbox_send_p99_ms | 38.4 |
| outbox_published_per_s | 492.0328 |
| outbox_backlog_max | {"outbox=account.outbox_event": 4.0, "outbox=payment.outbox_event": 81.0, "outbox=settlement.outbox_event": 5.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 1011.0, "group=payment-service,topic=fraud.events": 989.0, "group=payment-service,topic=settlement.events": 796.0, "group=fraud-service,topic=fraud.commands": 202.0, "group=settlement-service,topic=settlement.commands": 115.0} |
| consumer_p99_ms | {"consumer=ledger-service": 45.1, "consumer=payment-service": 51.0, "consumer=account-service": 58.4, "consumer=fraud-service": 48.0, "consumer=settlement-service": 124.1} |
| events_consumed_per_s | 447.3044 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 43.0 |
| hikari_acquire_max_ms | 2402.2 |
| hikari_acquire_avg_ms | 1.1 |
| hikari_usage_avg_ms | 9.6 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1232.7477 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 24.0 |
| mongo_cmd_max_ms | 1445.3 |
| mongo_cmd_avg_ms | 1.4 |
| jvm_heap_used_max_mb | 172 |
| jvm_heap_after_gc_max_mb | 123 |
| jvm_heap_committed_max_mb | 187 |
| gc_pause_max_ms | 168.0 |
| gc_pause_total_ms | 7017.7 |
| gc_count | 1977.7823 |
| alloc_rate_mb_s | 186.1 |
| threads_max | 74.0 |
| process_cpu_avg | 0.6394 |
| process_cpu_max | 0.9857 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.24 | 187 |
| payflow-kafka-1 | 106.5 | 180.8 | 808 |
| payflow-keycloak-1 | 0.2 | 0.17 | 4472 |
| payflow-mongo-1 | 15.4 | 54.75 | 227 |
| payflow-payflow-1 | 104.1 | 120.78 | 526 |
| payflow-postgres-1 | 41.5 | 50.88 | 679 |
| payflow-postgres-exporter-1 | 1.2 | 2.51 | 14 |
| payflow-prometheus-1 | 0.3 | 0.47 | 86 |
| payflow-settlement-rail-1 | 9.6 | 23.97 | 143 |
| payflow-tempo-1 | 1.6 | 5.0 | 100 |
