# Load-test report: 20260930-051546-burst-wip500-wsl

Window: 2026-09-30T05:15:49.575000+00:00 to 2026-09-30T05:22:30.958000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 16699 |
| iterations_per_s | 41.37738962323518 |
| dropped_iterations | 0 |
| payments_accepted | 10119 |
| payments_throttled | 6580 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.6975963968932396 |
| post_p50_ms | 10.030458 |
| post_p95_ms | 119.90572879999986 |
| post_p99_ms | 739.6024196600004 |
| post_max_ms | 5534.293523 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 25.2828 |
| api_5xx_ratio | 0.3837 |
| api_post_p50_ms | 7.7 |
| api_post_p95_ms | 91.7 |
| api_post_p99_ms | 665.7 |
| api_get_p99_ms | 140.9 |
| saga_completed | 10235.0237 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 10235.0237} |
| saga_p50_ms | 2167.0 |
| saga_p95_ms | 16741.4 |
| saga_p99_ms | 21395.7 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 7019.2, "step=AWAITING_CAPTURE": 6372.4, "step=AWAITING_FUNDS": 6575.6, "step=AWAITING_SETTLEMENT": 5708.3} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 157.0, "step=AWAITING_FUNDS": 27.0, "step=AWAITING_RISK": 103.0, "step=AWAITING_SETTLEMENT": 53.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 176.0, "step=AWAITING_FUNDS": 275.0, "step=AWAITING_RISK": 319.0, "step=AWAITING_SETTLEMENT": 145.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 206.3 |
| outbox_publish_delay_p99_ms | 1248.1 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 601.4, "outbox=payment.outbox_event": 1327.1, "outbox=settlement.outbox_event": 1132.8} |
| outbox_send_p99_ms | 60.1 |
| outbox_published_per_s | 278.2012 |
| outbox_backlog_max | {"outbox=account.outbox_event": 8.0, "outbox=payment.outbox_event": 49.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=fraud-service,topic=fraud.commands": 366.0, "group=payment-service,topic=funds.events": 344.0, "group=payment-service,topic=fraud.events": 192.0, "group=payment-service,topic=settlement.events": 153.0, "group=account-service,topic=funds.commands": 75.0} |
| consumer_p99_ms | {"consumer=ledger-service": 55.1, "consumer=payment-service": 63.2, "consumer=account-service": 81.9, "consumer=settlement-service": 120.4, "consumer=fraud-service": 91.0} |
| events_consumed_per_s | 252.9058 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 10.0 |
| hikari_acquire_max_ms | 3017.9 |
| hikari_acquire_avg_ms | 1.4 |
| hikari_usage_avg_ms | 9.8 |
| hikari_timeouts | 1.0074 |
| pg_commits_per_s | 712.4083 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 81.3 |
| mongo_cmd_avg_ms | 1.6 |
| jvm_heap_used_max_mb | 221 |
| jvm_heap_after_gc_max_mb | 107 |
| jvm_heap_committed_max_mb | 244 |
| gc_pause_max_ms | 32.0 |
| gc_pause_total_ms | 1736.6 |
| gc_count | 363.5779 |
| alloc_rate_mb_s | 97.2 |
| threads_max | 196.0 |
| process_cpu_avg | 0.5057 |
| process_cpu_max | 0.997 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 0.9 | 1.77 | 195 |
| payflow-kafka-1 | 58.6 | 196.06 | 700 |
| payflow-keycloak-1 | 5.5 | 162.93 | 718 |
| payflow-mongo-1 | 11.4 | 61.28 | 375 |
| payflow-payflow-1 | 101.2 | 200.38 | 644 |
| payflow-postgres-1 | 42.3 | 103.75 | 300 |
| payflow-postgres-exporter-1 | 0.3 | 1.82 | 18 |
| payflow-prometheus-1 | 0.5 | 1.93 | 92 |
| payflow-settlement-rail-1 | 1.9 | 10.01 | 43 |
| payflow-tempo-1 | 1.0 | 6.44 | 117 |
