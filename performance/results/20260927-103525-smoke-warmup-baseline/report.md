# Load-test report: 20260927-103525-smoke-warmup-baseline

Window: 2026-09-27T05:11:13.150000+00:00 to 2026-09-27T05:12:43.151000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 450 |
| iterations_per_s | 4.8723946168888395 |
| dropped_iterations | 0 |
| payments_accepted | 450 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 10.7401445 |
| post_p95_ms | 21.02041735 |
| post_p99_ms | 25.562228589999997 |
| post_max_ms | 47.606795 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 10.1 |
| api_post_p95_ms | 21.2 |
| api_post_p99_ms | 26.1 |
| api_get_p99_ms | 7.3 |
| saga_completed | 471.3739 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 471.3739} |
| saga_p50_ms | 1589.3 |
| saga_p95_ms | 2058.7 |
| saga_p99_ms | 2146.6 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 626.0, "step=AWAITING_RISK": 357.7, "step=AWAITING_SETTLEMENT": 447.0, "step=AWAITING_CAPTURE": 620.1} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 2.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 4.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 2.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 251.7 |
| outbox_publish_delay_p99_ms | 356.4 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=payment.outbox_event": 356.6, "outbox=settlement.outbox_event": 356.2, "outbox=account.outbox_event": 355.5} |
| outbox_send_p99_ms | 8.1 |
| outbox_published_per_s | 54.8471 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 1.0, "group=account-service,topic=funds.commands": 0.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=account-service": 11.6, "consumer=fraud-service": 14.8, "consumer=ledger-service": 11.2, "consumer=payment-service": 13.6, "consumer=settlement-service": 9.6} |
| events_consumed_per_s | 49.8706 |
| events_failed | {"category=CONCURRENCY": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 2.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 1.3 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 7.0 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 161.8706 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 44.0 |
| mongo_cmd_avg_ms | 0.7 |
| jvm_heap_used_max_mb | 156 |
| jvm_heap_after_gc_max_mb | 96 |
| jvm_heap_committed_max_mb | 215 |
| gc_pause_max_ms | 9.0 |
| gc_pause_total_ms | 226.2 |
| gc_count | 51.5565 |
| alloc_rate_mb_s | 30.8 |
| threads_max | 188.0 |
| process_cpu_avg | 0.2099 |
| process_cpu_max | 0.3937 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.79 | 319 |
| payflow-kafka-1 | 48.0 | 177.35 | 698 |
| payflow-keycloak-1 | 0.2 | 0.3 | 657 |
| payflow-mongo-1 | 13.5 | 47.25 | 472 |
| payflow-payflow-1 | 35.2 | 47.22 | 580 |
| payflow-postgres-1 | 9.2 | 15.91 | 236 |
| payflow-postgres-exporter-1 | 0.3 | 1.89 | 9 |
| payflow-prometheus-1 | 0.3 | 0.7 | 51 |
| payflow-tempo-1 | 0.6 | 2.29 | 56 |
