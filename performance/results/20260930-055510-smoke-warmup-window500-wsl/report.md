# Load-test report: 20260930-055510-smoke-warmup-window500-wsl

Window: 2026-09-30T05:55:15.068000+00:00 to 2026-09-30T05:56:50.688000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.516263084908627 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 13.989238 |
| post_p95_ms | 25.504889499999997 |
| post_p99_ms | 59.5804735 |
| post_max_ms | 1823.878537 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 4.5288 |
| api_5xx_ratio | None |
| api_post_p50_ms | 12.7 |
| api_post_p95_ms | 21.7 |
| api_post_p99_ms | 28.0 |
| api_get_p99_ms | 5.4 |
| saga_completed | 450.7899 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 450.7899} |
| saga_p50_ms | 1614.0 |
| saga_p95_ms | 1785.1 |
| saga_p99_ms | 3477.3 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 317.1, "step=AWAITING_CAPTURE": 536.5, "step=AWAITING_FUNDS": 2203.3, "step=AWAITING_SETTLEMENT": 2252.5} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 2.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 4.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 4.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 224.1 |
| outbox_publish_delay_p99_ms | 320.6 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 244.1, "outbox=payment.outbox_event": 328.4, "outbox=settlement.outbox_event": 324.5} |
| outbox_send_p99_ms | 18.4 |
| outbox_published_per_s | 49.7715 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=settlement-service,topic=settlement.commands": 1.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=account-service,topic=funds.commands": 0.0} |
| consumer_p99_ms | {"consumer=fraud-service": 32.2, "consumer=ledger-service": 13.9, "consumer=payment-service": 21.9, "consumer=account-service": 26.2, "consumer=settlement-service": 33.4} |
| events_consumed_per_s | 45.2427 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 4.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 62.3 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 5.4 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 153.9222 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 44.0 |
| mongo_cmd_avg_ms | 1.3 |
| jvm_heap_used_max_mb | 257 |
| jvm_heap_after_gc_max_mb | 103 |
| jvm_heap_committed_max_mb | 295 |
| gc_pause_max_ms | 33.0 |
| gc_pause_total_ms | 243.4 |
| gc_count | 18.0663 |
| alloc_rate_mb_s | 28.5 |
| threads_max | 194.0 |
| process_cpu_avg | 0.3301 |
| process_cpu_max | 0.618 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 1.87 | 170 |
| payflow-kafka-1 | 54.2 | 151.13 | 584 |
| payflow-keycloak-1 | 0.2 | 0.28 | 639 |
| payflow-mongo-1 | 5.8 | 30.87 | 191 |
| payflow-payflow-1 | 62.2 | 95.24 | 687 |
| payflow-postgres-1 | 10.8 | 13.2 | 110 |
| payflow-postgres-exporter-1 | 0.5 | 1.71 | 15 |
| payflow-prometheus-1 | 0.5 | 1.38 | 138 |
| payflow-settlement-rail-1 | 1.1 | 1.93 | 34 |
| payflow-tempo-1 | 0.4 | 0.71 | 84 |
