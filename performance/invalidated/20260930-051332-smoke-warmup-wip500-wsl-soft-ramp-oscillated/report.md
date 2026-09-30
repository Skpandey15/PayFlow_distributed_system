# Load-test report: 20260930-051332-smoke-warmup-wip500-wsl

Window: 2026-09-30T05:13:38.927000+00:00 to 2026-09-30T05:15:14.251000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.475673394578338 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 14.491975 |
| post_p95_ms | 110.83966699999999 |
| post_p99_ms | 213.683468 |
| post_max_ms | 271.411496 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 4.7063 |
| api_5xx_ratio | None |
| api_post_p50_ms | 13.4 |
| api_post_p95_ms | 136.6 |
| api_post_p99_ms | 227.9 |
| api_get_p99_ms | 9.2 |
| saga_completed | 461.1898 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 461.1898} |
| saga_p50_ms | 1645.7 |
| saga_p95_ms | 4679.6 |
| saga_p99_ms | 6759.2 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 1146.5, "step=AWAITING_CAPTURE": 2460.4, "step=AWAITING_FUNDS": 2411.2, "step=AWAITING_SETTLEMENT": 2617.8} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 2.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 13.0, "step=AWAITING_FUNDS": 8.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 6.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 227.0 |
| outbox_publish_delay_p99_ms | 937.7 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 1829.7, "outbox=payment.outbox_event": 877.3, "outbox=settlement.outbox_event": 1902.0} |
| outbox_send_p99_ms | 20.6 |
| outbox_published_per_s | 51.7133 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 2.0, "group=payment-service,topic=fraud.events": 1.0, "group=payment-service,topic=settlement.events": 1.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=ledger-service": 113.2, "consumer=payment-service": 200.3, "consumer=account-service": 224.3, "consumer=settlement-service": 423.3, "consumer=fraud-service": 42.6} |
| events_consumed_per_s | 46.9768 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 4.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 60.1 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 10.9 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 159.7364 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 37.9 |
| mongo_cmd_avg_ms | 1.4 |
| jvm_heap_used_max_mb | 176 |
| jvm_heap_after_gc_max_mb | 97 |
| jvm_heap_committed_max_mb | 209 |
| gc_pause_max_ms | 23.0 |
| gc_pause_total_ms | 272.4 |
| gc_count | 29.2402 |
| alloc_rate_mb_s | 26.7 |
| threads_max | 194.0 |
| process_cpu_avg | 0.3647 |
| process_cpu_max | 0.7507 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.5 | 4.75 | 194 |
| payflow-kafka-1 | 49.4 | 188.05 | 618 |
| payflow-keycloak-1 | 0.3 | 0.55 | 706 |
| payflow-mongo-1 | 13.4 | 43.42 | 224 |
| payflow-payflow-1 | 69.4 | 122.35 | 588 |
| payflow-postgres-1 | 11.5 | 13.45 | 117 |
| payflow-postgres-exporter-1 | 0.4 | 1.55 | 8 |
| payflow-prometheus-1 | 0.6 | 1.5 | 114 |
| payflow-settlement-rail-1 | 1.2 | 1.92 | 34 |
| payflow-tempo-1 | 0.5 | 0.82 | 98 |
