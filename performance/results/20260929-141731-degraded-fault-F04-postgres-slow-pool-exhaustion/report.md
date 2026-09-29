# Load-test report: 20260929-141731-degraded-fault-F04-postgres-slow-pool-exhaustion

Window: 2026-09-29T08:47:34.603000+00:00 to 2026-09-29T08:50:34.630000+00:00 (+30s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3555 |
| iterations_per_s | 19.57152300932324 |
| dropped_iterations | 46 |
| payments_accepted | 3025 |
| payments_throttled | 530 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.8857368006304176 |
| post_p50_ms | 18.667757 |
| post_p95_ms | 4081.4109289 |
| post_p99_ms | 5604.5196863400015 |
| post_max_ms | 7283.563932 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 16.7143 |
| api_5xx_ratio | 0.1511 |
| api_post_p50_ms | 16.7 |
| api_post_p95_ms | 4130.8 |
| api_post_p99_ms | 5713.8 |
| api_get_p99_ms | 3173.1 |
| saga_completed | 3098.7805 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3098.7805} |
| saga_p50_ms | 1756.8 |
| saga_p95_ms | 55432.7 |
| saga_p99_ms | 66583.0 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 5158.2, "step=AWAITING_FUNDS": 24576.8, "step=AWAITING_RISK": 49415.6, "step=AWAITING_SETTLEMENT": 5253.7} |
| drain_seconds_after_load | 30 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 10.0, "step=AWAITING_FUNDS": 10.0, "step=AWAITING_RISK": 4.0, "step=AWAITING_SETTLEMENT": 9.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 143.0, "step=AWAITING_FUNDS": 295.0, "step=AWAITING_RISK": 470.0, "step=AWAITING_SETTLEMENT": 203.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 215.8 |
| outbox_publish_delay_p99_ms | 17707.4 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 593.8, "outbox=payment.outbox_event": 20937.0, "outbox=settlement.outbox_event": 491.0} |
| outbox_send_p99_ms | 48.9 |
| outbox_published_per_s | 183.3429 |
| outbox_backlog_max | {"outbox=account.outbox_event": 4.0, "outbox=payment.outbox_event": 299.0, "outbox=settlement.outbox_event": 6.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 14.0, "outbox=settlement.outbox_event": 1.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 354.0, "group=payment-service,topic=fraud.events": 293.0, "group=fraud-service,topic=fraud.commands": 206.0, "group=payment-service,topic=settlement.events": 140.0, "group=account-service,topic=funds.commands": 54.0} |
| consumer_p99_ms | {"consumer=account-service": 62.1, "consumer=fraud-service": 60.5, "consumer=settlement-service": 87.7, "consumer=ledger-service": 32.6, "consumer=payment-service": 56.6} |
| events_consumed_per_s | 166.8229 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 24.3365, "category=CONCURRENCY": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 51.0 |
| hikari_acquire_max_ms | 3005.9 |
| hikari_acquire_avg_ms | 59.6 |
| hikari_usage_avg_ms | 34.5 |
| hikari_timeouts | 329.5442 |
| pg_commits_per_s | 470.9371 |
| pg_rollbacks | 6.1463 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 61.9 |
| mongo_cmd_avg_ms | 1.8 |
| jvm_heap_used_max_mb | 317 |
| jvm_heap_after_gc_max_mb | 112 |
| jvm_heap_committed_max_mb | 364 |
| gc_pause_max_ms | 19.0 |
| gc_pause_total_ms | 367.8 |
| gc_count | 67.6098 |
| alloc_rate_mb_s | 76.8 |
| threads_max | 195.0 |
| process_cpu_avg | 0.4263 |
| process_cpu_max | 0.995 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.6 | 4.79 | 338 |
| payflow-kafka-1 | 47.9 | 188.06 | 1195 |
| payflow-keycloak-1 | 3.7 | 48.04 | 757 |
| payflow-mongo-1 | 11.2 | 52.31 | 345 |
| payflow-payflow-1 | 86.5 | 206.96 | 734 |
| payflow-postgres-1 | 37.9 | 96.76 | 407 |
| payflow-postgres-exporter-1 | 0.4 | 1.9 | 10 |
| payflow-prometheus-1 | 0.7 | 2.4 | 79 |
| payflow-settlement-rail-1 | 0.9 | 2.87 | 56 |
| payflow-tempo-1 | 0.9 | 3.98 | 151 |
