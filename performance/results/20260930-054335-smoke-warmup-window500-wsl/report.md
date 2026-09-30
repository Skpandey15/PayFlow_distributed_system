# Load-test report: 20260930-054335-smoke-warmup-window500-wsl

Window: 2026-09-30T05:43:42.800000+00:00 to 2026-09-30T05:45:16.828000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.501609529271734 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 15.748724 |
| post_p95_ms | 36.036272499999995 |
| post_p99_ms | 81.15162699999999 |
| post_max_ms | 1781.384452 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 4.7639 |
| api_5xx_ratio | None |
| api_post_p50_ms | 14.8 |
| api_post_p95_ms | 32.9 |
| api_post_p99_ms | 59.8 |
| api_get_p99_ms | 9.4 |
| saga_completed | 456.5857 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 456.5857} |
| saga_p50_ms | 1619.2 |
| saga_p95_ms | 2081.3 |
| saga_p99_ms | 3735.3 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 407.4, "step=AWAITING_CAPTURE": 2235.4, "step=AWAITING_FUNDS": 2260.2, "step=AWAITING_SETTLEMENT": 2206.1} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 2.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 4.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 4.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 225.7 |
| outbox_publish_delay_p99_ms | 356.9 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 541.6, "outbox=payment.outbox_event": 355.8, "outbox=settlement.outbox_event": 402.4} |
| outbox_send_p99_ms | 22.6 |
| outbox_published_per_s | 52.2628 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 6.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=settlement.events": 1.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=account-service,topic=funds.commands": 0.0} |
| consumer_p99_ms | {"consumer=fraud-service": 66.2, "consumer=ledger-service": 35.8, "consumer=payment-service": 44.5, "consumer=account-service": 60.1, "consumer=settlement-service": 87.1} |
| events_consumed_per_s | 47.5083 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 4.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 38.1 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 7.1 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 162.1761 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 49.2 |
| mongo_cmd_avg_ms | 1.6 |
| jvm_heap_used_max_mb | 178 |
| jvm_heap_after_gc_max_mb | 97 |
| jvm_heap_committed_max_mb | 205 |
| gc_pause_max_ms | 41.0 |
| gc_pause_total_ms | 276.9 |
| gc_count | 29.1414 |
| alloc_rate_mb_s | 27.2 |
| threads_max | 194.0 |
| process_cpu_avg | 0.3742 |
| process_cpu_max | 0.764 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 0.9 | 1.0 | 185 |
| payflow-kafka-1 | 51.5 | 131.46 | 567 |
| payflow-keycloak-1 | 0.2 | 0.34 | 671 |
| payflow-mongo-1 | 7.6 | 42.73 | 225 |
| payflow-payflow-1 | 66.2 | 94.96 | 584 |
| payflow-postgres-1 | 11.9 | 20.38 | 111 |
| payflow-postgres-exporter-1 | 0.4 | 1.84 | 15 |
| payflow-prometheus-1 | 0.7 | 2.04 | 134 |
| payflow-settlement-rail-1 | 1.2 | 2.36 | 36 |
| payflow-tempo-1 | 0.5 | 1.11 | 118 |
