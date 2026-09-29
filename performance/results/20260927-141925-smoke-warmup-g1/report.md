# Load-test report: 20260927-141925-smoke-warmup-g1

Window: 2026-09-27T08:49:34.517000+00:00 to 2026-09-27T08:51:04.524000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 450 |
| iterations_per_s | 4.632335120126842 |
| dropped_iterations | 0 |
| payments_accepted | 450 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 22.038983 |
| post_p95_ms | 63.005353299999854 |
| post_p99_ms | 183.93097297999986 |
| post_max_ms | 336.080535 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0061 |
| api_5xx_ratio | None |
| api_post_p50_ms | 21.1 |
| api_post_p95_ms | 61.1 |
| api_post_p99_ms | 187.1 |
| api_get_p99_ms | 10.8 |
| saga_completed | 452.6284 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 452.6284} |
| saga_p50_ms | 1660.7 |
| saga_p95_ms | 2113.1 |
| saga_p99_ms | 2952.9 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 797.9, "step=AWAITING_FUNDS": 870.6, "step=AWAITING_RISK": 515.4, "step=AWAITING_SETTLEMENT": 871.5} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 2.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 4.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 4.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 234.0 |
| outbox_publish_delay_p99_ms | 424.1 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 402.7, "outbox=payment.outbox_event": 425.0, "outbox=settlement.outbox_event": 442.0} |
| outbox_send_p99_ms | 21.7 |
| outbox_published_per_s | 54.3463 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 8.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 1.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=ledger-service": 54.1, "consumer=payment-service": 95.7, "consumer=account-service": 130.0, "consumer=fraud-service": 59.2, "consumer=settlement-service": 153.5} |
| events_consumed_per_s | 49.4421 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 8.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 11.9 |
| hikari_acquire_avg_ms | 0.1 |
| hikari_usage_avg_ms | 12.0 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 160.8235 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 24.0 |
| mongo_cmd_max_ms | 97.0 |
| mongo_cmd_avg_ms | 2.1 |
| jvm_heap_used_max_mb | 169 |
| jvm_heap_after_gc_max_mb | 73 |
| jvm_heap_committed_max_mb | 180 |
| gc_pause_max_ms | 46.0 |
| gc_pause_total_ms | 250.1 |
| gc_count | 28.2333 |
| alloc_rate_mb_s | 28.4 |
| threads_max | 71.0 |
| process_cpu_avg | 0.3721 |
| process_cpu_max | 0.628 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 1.72 | 179 |
| payflow-kafka-1 | 36.8 | 143.93 | 716 |
| payflow-keycloak-1 | 4.5 | 51.59 | 2770 |
| payflow-mongo-1 | 9.3 | 52.61 | 193 |
| payflow-payflow-1 | 64.5 | 136.71 | 558 |
| payflow-postgres-1 | 17.5 | 50.63 | 791 |
| payflow-postgres-exporter-1 | 0.4 | 2.09 | 14 |
| payflow-prometheus-1 | 1.2 | 5.17 | 85 |
| payflow-settlement-rail-1 | 0.7 | 2.87 | 42 |
| payflow-tempo-1 | 1.2 | 9.31 | 127 |
