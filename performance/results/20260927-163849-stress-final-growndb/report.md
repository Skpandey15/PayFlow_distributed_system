# Load-test report: 20260927-163849-stress-final

Window: 2026-09-27T11:08:56.610000+00:00 to 2026-09-27T11:18:56.699000+00:00 (+93s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 112875 |
| iterations_per_s | 186.30659070741885 |
| dropped_iterations | 225 |
| payments_accepted | 31267 |
| payments_throttled | 81608 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.3651406122369598 |
| post_p50_ms | 1.97853 |
| post_p95_ms | 116.6086015999995 |
| post_p99_ms | 349.55340015999997 |
| post_max_ms | 3870.466263 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 52.4941 |
| api_5xx_ratio | 0.7231 |
| api_post_p50_ms | 0.9 |
| api_post_p95_ms | 98.9 |
| api_post_p99_ms | 320.1 |
| api_get_p99_ms | 352.5 |
| saga_completed | 31616.9869 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 31616.9869} |
| saga_p50_ms | 203307.9 |
| saga_p95_ms | 247776.4 |
| saga_p99_ms | 265877.0 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 131448.6, "step=AWAITING_RISK": 155828.5, "step=AWAITING_SETTLEMENT": 86867.1, "step=AWAITING_CAPTURE": 91622.0} |
| drain_seconds_after_load | 93 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 2986.0, "step=AWAITING_FUNDS": 6150.0, "step=AWAITING_RISK": 576.0, "step=AWAITING_SETTLEMENT": 3067.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 9217.0, "step=AWAITING_FUNDS": 7597.0, "step=AWAITING_RISK": 11661.0, "step=AWAITING_SETTLEMENT": 5823.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 157.6 |
| outbox_publish_delay_p99_ms | 2457.8 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 3302.0, "outbox=payment.outbox_event": 2634.9, "outbox=settlement.outbox_event": 536.6} |
| outbox_send_p99_ms | 50.2 |
| outbox_published_per_s | 494.2336 |
| outbox_backlog_max | {"outbox=account.outbox_event": 6.0, "outbox=payment.outbox_event": 105.0, "outbox=settlement.outbox_event": 4.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 1.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 9105.0, "group=payment-service,topic=fraud.events": 8625.0, "group=fraud-service,topic=fraud.commands": 6601.0, "group=payment-service,topic=settlement.events": 4878.0, "group=settlement-service,topic=settlement.commands": 984.0} |
| consumer_p99_ms | {"consumer=account-service": 93.4, "consumer=fraud-service": 83.3, "consumer=settlement-service": 107.8, "consumer=ledger-service": 52.3, "consumer=payment-service": 73.2} |
| events_consumed_per_s | 444.3983 |
| events_failed | {"category=CONCURRENCY": 15.2064} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 49.0 |
| hikari_acquire_max_ms | 3004.9 |
| hikari_acquire_avg_ms | 3.1 |
| hikari_usage_avg_ms | 11.3 |
| hikari_timeouts | 2.0234 |
| pg_commits_per_s | 1251.5916 |
| pg_rollbacks | 16.0696 |
| pg_deadlocks | 0 |
| pg_backends_max | 24.0 |
| mongo_cmd_max_ms | 2001.2 |
| mongo_cmd_avg_ms | 1.7 |
| jvm_heap_used_max_mb | 240 |
| jvm_heap_after_gc_max_mb | 95 |
| jvm_heap_committed_max_mb | 271 |
| gc_pause_max_ms | 44.0 |
| gc_pause_total_ms | 5183.8 |
| gc_count | 1063.2745 |
| alloc_rate_mb_s | 204.4 |
| threads_max | 75.0 |
| process_cpu_avg | 0.74 |
| process_cpu_max | 0.9959 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 1.52 | 180 |
| payflow-kafka-1 | 50.1 | 148.04 | 769 |
| payflow-keycloak-1 | 21.1 | 125.41 | 3836 |
| payflow-mongo-1 | 15.7 | 41.87 | 395 |
| payflow-payflow-1 | 74.1 | 90.3 | 589 |
| payflow-postgres-1 | 50.2 | 74.32 | 674 |
| payflow-postgres-exporter-1 | 0.8 | 1.91 | 13 |
| payflow-prometheus-1 | 0.3 | 0.53 | 81 |
| payflow-settlement-rail-1 | 4.4 | 22.54 | 148 |
| payflow-tempo-1 | 0.9 | 3.16 | 96 |
