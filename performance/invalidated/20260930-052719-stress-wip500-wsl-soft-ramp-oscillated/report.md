# Load-test report: 20260930-052719-stress-wip500-wsl

Window: 2026-09-30T05:27:22.234000+00:00 to 2026-09-30T05:37:55.821000+00:00 (+33s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 112996 |
| iterations_per_s | 177.68164766290923 |
| dropped_iterations | 104 |
| payments_accepted | 28089 |
| payments_throttled | 84907 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.33150410984788836 |
| post_p50_ms | 2.3524855000000002 |
| post_p95_ms | 58.87846675 |
| post_p99_ms | 167.33487260000027 |
| post_max_ms | 5509.498538 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 44.546 |
| api_5xx_ratio | 0.752 |
| api_post_p50_ms | 0.7 |
| api_post_p95_ms | 43.4 |
| api_post_p99_ms | 127.6 |
| api_get_p99_ms | 98.8 |
| saga_completed | 28303.1435 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 28303.1435} |
| saga_p50_ms | 9754.1 |
| saga_p95_ms | 29321.5 |
| saga_p99_ms | 43615.3 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 16027.4, "step=AWAITING_CAPTURE": 14094.7, "step=AWAITING_FUNDS": 13691.0, "step=AWAITING_SETTLEMENT": 11092.9} |
| drain_seconds_after_load | 33 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 113.0, "step=AWAITING_FUNDS": 119.0, "step=AWAITING_RISK": 145.0, "step=AWAITING_SETTLEMENT": 99.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 244.0, "step=AWAITING_FUNDS": 398.0, "step=AWAITING_RISK": 378.0, "step=AWAITING_SETTLEMENT": 199.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 154.6 |
| outbox_publish_delay_p99_ms | 620.5 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=settlement.outbox_event": 673.0, "outbox=account.outbox_event": 506.6, "outbox=payment.outbox_event": 649.5} |
| outbox_send_p99_ms | 64.5 |
| outbox_published_per_s | 487.4473 |
| outbox_backlog_max | {"outbox=account.outbox_event": 5.0, "outbox=payment.outbox_event": 60.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 491.0, "group=payment-service,topic=fraud.events": 321.0, "group=payment-service,topic=settlement.events": 187.0, "group=fraud-service,topic=fraud.commands": 174.0, "group=account-service,topic=funds.commands": 91.0} |
| consumer_p99_ms | {"consumer=ledger-service": 50.6, "consumer=payment-service": 60.6, "consumer=account-service": 72.5, "consumer=settlement-service": 111.0, "consumer=fraud-service": 77.3} |
| events_consumed_per_s | 442.9824 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 48.0 |
| hikari_acquire_max_ms | 4633.0 |
| hikari_acquire_avg_ms | 1.6 |
| hikari_usage_avg_ms | 11.3 |
| hikari_timeouts | 14.0919 |
| pg_commits_per_s | 1227.1619 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 242.1 |
| mongo_cmd_avg_ms | 1.6 |
| jvm_heap_used_max_mb | 252 |
| jvm_heap_after_gc_max_mb | 104 |
| jvm_heap_committed_max_mb | 273 |
| gc_pause_max_ms | 45.0 |
| gc_pause_total_ms | 5200.3 |
| gc_count | 858.4954 |
| alloc_rate_mb_s | 180.3 |
| threads_max | 196.0 |
| process_cpu_avg | 0.7216 |
| process_cpu_max | 0.996 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 0.8 | 1.58 | 180 |
| payflow-kafka-1 | 63.4 | 173.95 | 662 |
| payflow-keycloak-1 | 1.4 | 52.57 | 720 |
| payflow-mongo-1 | 19.9 | 56.45 | 328 |
| payflow-payflow-1 | 153.8 | 207.34 | 612 |
| payflow-postgres-1 | 69.4 | 143.86 | 531 |
| payflow-postgres-exporter-1 | 0.4 | 2.39 | 15 |
| payflow-prometheus-1 | 0.5 | 1.98 | 94 |
| payflow-settlement-rail-1 | 2.7 | 8.19 | 41 |
| payflow-tempo-1 | 3.5 | 66.41 | 1368 |
