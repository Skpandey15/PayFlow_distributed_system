# Load-test report: 20260930-045851-stress-lag5000-wsl

Window: 2026-09-30T04:58:53.251000+00:00 to 2026-09-30T05:09:27.637000+00:00 (+96s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 113099 |
| iterations_per_s | 177.8226313423467 |
| dropped_iterations | 0 |
| payments_accepted | 42894 |
| payments_throttled | 70205 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.4776685738093998 |
| post_p50_ms | 2.694291 |
| post_p95_ms | 59.392364099999924 |
| post_p99_ms | 169.08446808000016 |
| post_max_ms | 2185.874244 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 68.2961 |
| api_5xx_ratio | 0.6192 |
| api_post_p50_ms | 0.9 |
| api_post_p95_ms | 43.4 |
| api_post_p99_ms | 132.0 |
| api_get_p99_ms | 89.9 |
| saga_completed | 43291.7872 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 43291.7872} |
| saga_p50_ms | 111531.4 |
| saga_p95_ms | 195295.0 |
| saga_p99_ms | 214515.1 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 86775.5, "step=AWAITING_CAPTURE": 89471.9, "step=AWAITING_FUNDS": 79782.0, "step=AWAITING_SETTLEMENT": 51502.0} |
| drain_seconds_after_load | 96 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 1296.0, "step=AWAITING_FUNDS": 5288.0, "step=AWAITING_RISK": 1744.0, "step=AWAITING_SETTLEMENT": 447.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 6161.0, "step=AWAITING_FUNDS": 5288.0, "step=AWAITING_RISK": 6014.0, "step=AWAITING_SETTLEMENT": 3205.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 146.1 |
| outbox_publish_delay_p99_ms | 525.5 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 497.2, "outbox=payment.outbox_event": 522.9, "outbox=settlement.outbox_event": 609.6} |
| outbox_send_p99_ms | 50.2 |
| outbox_published_per_s | 682.3633 |
| outbox_backlog_max | {"outbox=account.outbox_event": 8.0, "outbox=payment.outbox_event": 244.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 7800.0, "group=payment-service,topic=fraud.events": 5898.0, "group=payment-service,topic=settlement.events": 2812.0, "group=fraud-service,topic=fraud.commands": 2602.0, "group=settlement-service,topic=settlement.commands": 761.0} |
| consumer_p99_ms | {"consumer=fraud-service": 62.5, "consumer=ledger-service": 40.5, "consumer=payment-service": 46.2, "consumer=account-service": 54.0, "consumer=settlement-service": 78.1} |
| events_consumed_per_s | 621.5698 |
| events_failed | {"category=CONCURRENCY": 8.102} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 40.0 |
| hikari_acquire_max_ms | 1274.8 |
| hikari_acquire_avg_ms | 0.6 |
| hikari_usage_avg_ms | 7.3 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1731.5307 |
| pg_rollbacks | 10.033 |
| pg_deadlocks | 0 |
| pg_backends_max | 24.0 |
| mongo_cmd_max_ms | 116.3 |
| mongo_cmd_avg_ms | 1.3 |
| jvm_heap_used_max_mb | 282 |
| jvm_heap_after_gc_max_mb | 116 |
| jvm_heap_committed_max_mb | 306 |
| gc_pause_max_ms | 40.0 |
| gc_pause_total_ms | 4714.4 |
| gc_count | 970.3419 |
| alloc_rate_mb_s | 238.0 |
| threads_max | 197.0 |
| process_cpu_avg | 0.8229 |
| process_cpu_max | 0.994 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 0.9 | 6.3 | 218 |
| payflow-kafka-1 | 66.9 | 197.24 | 770 |
| payflow-keycloak-1 | 2.3 | 98.29 | 765 |
| payflow-mongo-1 | 23.5 | 115.92 | 387 |
| payflow-payflow-1 | 172.1 | 206.68 | 728 |
| payflow-postgres-1 | 85.3 | 138.11 | 695 |
| payflow-postgres-exporter-1 | 0.3 | 1.93 | 18 |
| payflow-prometheus-1 | 0.5 | 1.96 | 76 |
| payflow-settlement-rail-1 | 2.6 | 8.13 | 45 |
| payflow-tempo-1 | 4.0 | 85.03 | 962 |
