# Load-test report: 20260927-134943-stress-wp03

Window: 2026-09-27T08:19:48.321000+00:00 to 2026-09-27T08:29:48.562000+00:00 (+790s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 80733 |
| iterations_per_s | 133.70329846998422 |
| dropped_iterations | 32366 |
| payments_accepted | 54189 |
| payments_throttled | 6942 |
| payments_rejected_at_api | 18796 |
| checks_pass_rate | 0.7362853214355866 |
| post_p50_ms | 535.491046 |
| post_p95_ms | 12260.446108199998 |
| post_p99_ms | 44816.562857680015 |
| post_max_ms | 59263.510332 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 90.9227 |
| api_5xx_ratio | 0.3176 |
| api_post_p50_ms | 495.2 |
| api_post_p95_ms | 10000.0 |
| api_post_p99_ms | 10000.0 |
| api_get_p99_ms | 10000.0 |
| saga_completed | 54384.6282 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 54384.6282} |
| saga_p50_ms | 600000.0 |
| saga_p95_ms | 600000.0 |
| saga_p99_ms | 600000.0 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 227773.5, "step=AWAITING_FUNDS": 247301.6, "step=AWAITING_RISK": 600000.0, "step=AWAITING_SETTLEMENT": 165769.8} |
| drain_seconds_after_load | 790 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 413.0, "step=AWAITING_FUNDS": 2189.0, "step=AWAITING_RISK": 41721.0, "step=AWAITING_SETTLEMENT": 988.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 18306.0, "step=AWAITING_FUNDS": 16176.0, "step=AWAITING_RISK": 41721.0, "step=AWAITING_SETTLEMENT": 5829.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 167.0 |
| outbox_publish_delay_p99_ms | 66172.4 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 916.5, "outbox=payment.outbox_event": 68006.9, "outbox=settlement.outbox_event": 801.3} |
| outbox_send_p99_ms | 47.9 |
| outbox_published_per_s | 336.6807 |
| outbox_backlog_max | {"outbox=account.outbox_event": 6.0, "outbox=payment.outbox_event": 6645.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 95.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 33461.0, "group=payment-service,topic=funds.events": 26288.0, "group=fraud-service,topic=fraud.commands": 24706.0, "group=payment-service,topic=settlement.events": 5721.0, "group=account-service,topic=funds.commands": 1462.0} |
| consumer_p99_ms | {"consumer=ledger-service": 44.3, "consumer=payment-service": 65.9, "consumer=account-service": 104.0, "consumer=fraud-service": 108.5, "consumer=settlement-service": 121.4} |
| events_consumed_per_s | 205.9798 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 143.7616, "category=CONCURRENCY": 12.1764} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 1292.0 |
| hikari_acquire_max_ms | 43223.4 |
| hikari_acquire_avg_ms | 213.9 |
| hikari_usage_avg_ms | 17.3 |
| hikari_timeouts | 20940.334 |
| pg_commits_per_s | 1135.0403 |
| pg_rollbacks | 39.1408 |
| pg_deadlocks | 0 |
| pg_backends_max | 24.0 |
| mongo_cmd_max_ms | 6073.6 |
| mongo_cmd_avg_ms | 2.1 |
| jvm_heap_used_max_mb | 1062 |
| jvm_heap_after_gc_max_mb | 764 |
| jvm_heap_committed_max_mb | 1114 |
| gc_pause_max_ms | 38564.0 |
| gc_pause_total_ms | 107436.8 |
| gc_count | 1652.9609 |
| alloc_rate_mb_s | 202.5 |
| threads_max | 74.0 |
| process_cpu_avg | 0.645 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 5.7 | 182.03 | 253 |
| payflow-kafka-1 | 87.8 | 261.16 | 886 |
| payflow-keycloak-1 | 84.8 | 696.57 | 4786 |
| payflow-mongo-1 | 29.9 | 153.49 | 380 |
| payflow-payflow-1 | 143.6 | 342.89 | 1533 |
| payflow-postgres-1 | 90.4 | 396.16 | 650 |
| payflow-postgres-exporter-1 | 0.7 | 8.11 | 16 |
| payflow-prometheus-1 | 4.6 | 80.48 | 90 |
| payflow-settlement-rail-1 | 2.9 | 109.75 | 58 |
| payflow-tempo-1 | 3.5 | 21.81 | 118 |
