# Load-test report: 

Window: 2026-09-27T14:22:37.458000+00:00 to 2026-09-27T14:32:10.038000+00:00 (+60s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 50722 |
| iterations_per_s | 88.34203975505874 |
| dropped_iterations | 35228 |
| payments_accepted | 17417 |
| payments_throttled | 23721 |
| payments_rejected_at_api | 172 |
| checks_pass_rate | 0.5207329989408684 |
| post_p50_ms | 57.618902 |
| post_p95_ms | 3567.9792561499958 |
| post_p99_ms | 16992.89961845999 |
| post_max_ms | 44425.259831 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 30.514 |
| api_5xx_ratio | 0.5762 |
| api_post_p50_ms | 2.9 |
| api_post_p95_ms | 769.0 |
| api_post_p99_ms | 5047.4 |
| api_get_p99_ms | 2900.6 |
| saga_completed | 3862.602 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3862.602} |
| saga_p50_ms | 3266.0 |
| saga_p95_ms | 410639.2 |
| saga_p99_ms | 520000.7 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 376476.2, "step=AWAITING_FUNDS": 443886.4, "step=AWAITING_RISK": 442788.6, "step=AWAITING_SETTLEMENT": 387079.5} |
| drain_seconds_after_load | 60 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 592.0, "step=AWAITING_FUNDS": 2721.0, "step=AWAITING_RISK": 9836.0, "step=AWAITING_SETTLEMENT": 614.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 593.0, "step=AWAITING_FUNDS": 7208.0, "step=AWAITING_RISK": 4725.0, "step=AWAITING_SETTLEMENT": 1051.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 717.0, "step=AWAITING_FUNDS": 7208.0, "step=AWAITING_RISK": 9836.0, "step=AWAITING_SETTLEMENT": 1051.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 211.5 |
| outbox_publish_delay_p99_ms | 55510.3 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 4666.3, "outbox=payment.outbox_event": 57224.2, "outbox=settlement.outbox_event": 1663.5} |
| outbox_send_p99_ms | 231.4 |
| outbox_published_per_s | 143.3596 |
| outbox_backlog_max | {"outbox=account.outbox_event": 3.0, "outbox=payment.outbox_event": 696.0, "outbox=settlement.outbox_event": 5.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 65.0, "outbox=payment.outbox_event": 53.0, "outbox=settlement.outbox_event": 58.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 6323.0, "group=account-service,topic=funds.commands": 5552.0, "group=fraud-service,topic=fraud.commands": 3681.0, "group=payment-service,topic=funds.events": 2970.0, "group=payment-service,topic=settlement.events": 897.0} |
| consumer_p99_ms | {"consumer=settlement-service": 532.1, "consumer=account-service": 641.6, "consumer=fraud-service": 203.8, "consumer=ledger-service": 104.8, "consumer=payment-service": 261.5} |
| events_consumed_per_s | 107.342 |
| events_failed | {"category=CONCURRENCY": 2.029, "category=TRANSIENT_INFRASTRUCTURE": 45.6572, "category=UNKNOWN": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 56.0 |
| hikari_acquire_max_ms | 14560.2 |
| hikari_acquire_avg_ms | 44.8 |
| hikari_usage_avg_ms | 45.1 |
| hikari_timeouts | 464.3571 |
| pg_commits_per_s | 448.8779 |
| pg_rollbacks | 16.0777 |
| pg_deadlocks | 0 |
| pg_backends_max | 24.0 |
| mongo_cmd_max_ms | 18452.5 |
| mongo_cmd_avg_ms | 7.6 |
| jvm_heap_used_max_mb | 419 |
| jvm_heap_after_gc_max_mb | 145 |
| jvm_heap_committed_max_mb | 857 |
| gc_pause_max_ms | 9062.0 |
| gc_pause_total_ms | 29106.6 |
| gc_count | 298.4854 |
| alloc_rate_mb_s | 66.8 |
| threads_max | 74.0 |
| process_cpu_avg | 0.5901 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 0.8 | 0.85 | 306 |
| payflow-kafka-1 | 72.5 | 128.28 | 811 |
| payflow-keycloak-1 | 0.2 | 0.17 | 1521 |
| payflow-mongo-1 | 71.9 | 100.51 | 377 |
| payflow-payflow-1 | 123.7 | 128.46 | 630 |
| payflow-postgres-1 | 43.2 | 49.5 | 512 |
| payflow-postgres-exporter-1 | 0.0 | 0.0 | 10 |
| payflow-prometheus-1 | 0.4 | 0.53 | 82 |
| payflow-settlement-rail-1 | 1.2 | 1.27 | 38 |
| payflow-tempo-1 | 8.9 | 16.74 | 805 |
