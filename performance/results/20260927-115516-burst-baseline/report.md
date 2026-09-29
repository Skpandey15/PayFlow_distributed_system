# Load-test report: 20260927-115516-burst-baseline

Window: 2026-09-27T06:25:18.991000+00:00 to 2026-09-27T06:31:39.007000+00:00 (+342s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 16699 |
| iterations_per_s | 43.72950029230072 |
| dropped_iterations | 0 |
| payments_accepted | 16696 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 7.1413545 |
| post_p95_ms | 31.9856565 |
| post_p99_ms | 156.47853199999994 |
| post_max_ms | 415.209175 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 44.2613 |
| api_5xx_ratio | None |
| api_post_p50_ms | 6.8 |
| api_post_p95_ms | 27.9 |
| api_post_p99_ms | 152.7 |
| api_get_p99_ms | 6.7 |
| saga_completed | 2805.7722 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 2805.7722} |
| saga_p50_ms | 467150.0 |
| saga_p95_ms | 600000.0 |
| saga_p99_ms | 600000.0 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 364295.7, "step=AWAITING_FUNDS": 360289.0, "step=AWAITING_RISK": 359958.7, "step=AWAITING_SETTLEMENT": 364081.0} |
| drain_seconds_after_load | 342 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 1400.0, "step=AWAITING_FUNDS": 2068.0, "step=AWAITING_RISK": 7782.0, "step=AWAITING_SETTLEMENT": 4187.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 4206.0, "step=AWAITING_FUNDS": 7790.0, "step=AWAITING_RISK": 81.0, "step=AWAITING_SETTLEMENT": 2066.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 4427.0, "step=AWAITING_FUNDS": 7790.0, "step=AWAITING_RISK": 7782.0, "step=AWAITING_SETTLEMENT": 4376.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 111880.5 |
| outbox_publish_delay_p99_ms | 361847.9 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 1244.5, "outbox=payment.outbox_event": 362883.2, "outbox=settlement.outbox_event": 892.7} |
| outbox_send_p99_ms | 10.2 |
| outbox_published_per_s | 120.9813 |
| outbox_backlog_max | {"outbox=account.outbox_event": 3.0, "outbox=payment.outbox_event": 32580.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 333.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 41.0, "group=payment-service,topic=fraud.events": 27.0, "group=payment-service,topic=funds.events": 26.0, "group=payment-service,topic=settlement.events": 21.0, "group=settlement-service,topic=settlement.commands": 13.0} |
| consumer_p99_ms | {"consumer=payment-service": 41.6, "consumer=account-service": 82.0, "consumer=fraud-service": 25.3, "consumer=settlement-service": 88.7, "consumer=ledger-service": 9.7} |
| events_consumed_per_s | 127.1836 |
| events_failed | {"category=CONCURRENCY": 10.0846} |
| events_dead_lettered | 0 |
| hikari_active_max | 15.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 183.1 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 7.7 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 594.808 |
| pg_rollbacks | 12.0552 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 64.7 |
| mongo_cmd_avg_ms | 0.7 |
| jvm_heap_used_max_mb | 169 |
| jvm_heap_after_gc_max_mb | 105 |
| jvm_heap_committed_max_mb | 232 |
| gc_pause_max_ms | 210.0 |
| gc_pause_total_ms | 3250.0 |
| gc_count | 777.1528 |
| alloc_rate_mb_s | 83.8 |
| threads_max | 190.0 |
| process_cpu_avg | 0.4131 |
| process_cpu_max | 0.816 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.63 | 320 |
| payflow-kafka-1 | 53.3 | 195.8 | 656 |
| payflow-keycloak-1 | 2.6 | 127.33 | 926 |
| payflow-mongo-1 | 15.1 | 58.23 | 365 |
| payflow-payflow-1 | 81.9 | 189.19 | 565 |
| payflow-postgres-1 | 27.6 | 81.14 | 248 |
| payflow-postgres-exporter-1 | 0.3 | 2.05 | 9 |
| payflow-prometheus-1 | 0.5 | 1.59 | 70 |
| payflow-tempo-1 | 0.9 | 6.05 | 121 |
