# Load-test report: 20260927-145540-smoke-warmup-g1-limit

Window: 2026-09-27T09:26:01.521000+00:00 to 2026-09-27T09:27:31.542000+00:00 (+30s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.782300461376187 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 16.944035 |
| post_p95_ms | 36.084322 |
| post_p99_ms | 56.745964 |
| post_max_ms | 88.971479 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 15.6 |
| api_post_p95_ms | 34.2 |
| api_post_p99_ms | 54.1 |
| api_get_p99_ms | 21.4 |
| saga_completed | 457.6103 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 457.6103} |
| saga_p50_ms | 1616.0 |
| saga_p95_ms | 1785.8 |
| saga_p99_ms | 2475.0 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 416.1, "step=AWAITING_SETTLEMENT": 624.4, "step=AWAITING_CAPTURE": 581.5, "step=AWAITING_FUNDS": 583.8} |
| drain_seconds_after_load | 30 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 2.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 4.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 228.4 |
| outbox_publish_delay_p99_ms | 341.4 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 244.2, "outbox=payment.outbox_event": 345.0, "outbox=settlement.outbox_event": 343.8} |
| outbox_send_p99_ms | 21.9 |
| outbox_published_per_s | 54.6491 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 6.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 1.0, "group=account-service,topic=funds.commands": 0.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=ledger-service": 20.4, "consumer=payment-service": 27.4, "consumer=account-service": 35.6, "consumer=fraud-service": 33.5, "consumer=settlement-service": 48.8} |
| events_consumed_per_s | 49.682 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 5.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 13.8 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 7.1 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 159.2706 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 21.9 |
| mongo_cmd_avg_ms | 1.5 |
| jvm_heap_used_max_mb | 189 |
| jvm_heap_after_gc_max_mb | 73 |
| jvm_heap_committed_max_mb | 207 |
| gc_pause_max_ms | 65.0 |
| gc_pause_total_ms | 223.3 |
| gc_count | 22.9583 |
| alloc_rate_mb_s | 26.2 |
| threads_max | 70.0 |
| process_cpu_avg | 0.3563 |
| process_cpu_max | 0.688 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 2.27 | 186 |
| payflow-kafka-1 | 31.6 | 161.97 | 885 |
| payflow-keycloak-1 | 0.8 | 5.26 | 2945 |
| payflow-mongo-1 | 9.9 | 45.86 | 363 |
| payflow-payflow-1 | 77.6 | 150.58 | 568 |
| payflow-postgres-1 | 14.5 | 24.2 | 600 |
| payflow-postgres-exporter-1 | 0.4 | 1.96 | 14 |
| payflow-prometheus-1 | 0.8 | 2.51 | 90 |
| payflow-settlement-rail-1 | 0.3 | 0.4 | 64 |
