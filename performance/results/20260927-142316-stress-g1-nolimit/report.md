# Load-test report: 20260927-142316-stress-g1-nolimit

Window: 2026-09-27T08:53:23.189000+00:00 to 2026-09-27T09:03:23.389000+00:00 (+1189s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 102220 |
| iterations_per_s | 168.91477511264162 |
| dropped_iterations | 10879 |
| payments_accepted | 88506 |
| payments_throttled | 12867 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.9027170775954482 |
| post_p50_ms | 55.50816 |
| post_p95_ms | 4003.6185037999935 |
| post_p99_ms | 6687.464286199999 |
| post_max_ms | 18089.909621 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 148.605 |
| api_5xx_ratio | 0.1283 |
| api_post_p50_ms | 42.3 |
| api_post_p95_ms | 3438.5 |
| api_post_p99_ms | 5435.5 |
| api_get_p99_ms | 3377.7 |
| saga_completed | 88703.3305 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 88703.3305} |
| saga_p50_ms | 600000.0 |
| saga_p95_ms | 600000.0 |
| saga_p99_ms | 600000.0 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 421785.0, "step=AWAITING_RISK": 600000.0, "step=AWAITING_SETTLEMENT": 267348.9, "step=AWAITING_CAPTURE": 423840.8} |
| drain_seconds_after_load | 1189 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 4214.0, "step=AWAITING_FUNDS": 4574.0, "step=AWAITING_RISK": 65616.0, "step=AWAITING_SETTLEMENT": 2946.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 29219.0, "step=AWAITING_FUNDS": 27587.0, "step=AWAITING_RISK": 65616.0, "step=AWAITING_SETTLEMENT": 5609.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 157.6 |
| outbox_publish_delay_p99_ms | 18996.9 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=settlement.outbox_event": 650.5, "outbox=account.outbox_event": 880.3, "outbox=payment.outbox_event": 20712.9} |
| outbox_send_p99_ms | 75.6 |
| outbox_published_per_s | 564.1714 |
| outbox_backlog_max | {"outbox=account.outbox_event": 10.0, "outbox=payment.outbox_event": 12840.0, "outbox=settlement.outbox_event": 5.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 56.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 57669.0, "group=payment-service,topic=funds.events": 45271.0, "group=fraud-service,topic=fraud.commands": 36887.0, "group=payment-service,topic=settlement.events": 5097.0, "group=account-service,topic=funds.commands": 1527.0} |
| consumer_p99_ms | {"consumer=ledger-service": 46.5, "consumer=payment-service": 58.1, "consumer=account-service": 75.8, "consumer=fraud-service": 107.2, "consumer=settlement-service": 104.9} |
| events_consumed_per_s | 359.7129 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 115.4293, "category=CONCURRENCY": 17.0579} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 1174.0 |
| hikari_acquire_max_ms | 14319.8 |
| hikari_acquire_avg_ms | 61.2 |
| hikari_usage_avg_ms | 11.1 |
| hikari_timeouts | 13616.5233 |
| pg_commits_per_s | 1877.2084 |
| pg_rollbacks | 22.1112 |
| pg_deadlocks | 0 |
| pg_backends_max | 24.0 |
| mongo_cmd_max_ms | 3884.6 |
| mongo_cmd_avg_ms | 1.9 |
| jvm_heap_used_max_mb | 826 |
| jvm_heap_after_gc_max_mb | 606 |
| jvm_heap_committed_max_mb | 961 |
| gc_pause_max_ms | 437.0 |
| gc_pause_total_ms | 12351.6 |
| gc_count | 1413.1597 |
| alloc_rate_mb_s | 219.9 |
| threads_max | 74.0 |
| process_cpu_avg | 0.8114 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 2.0 | 40.41 | 181 |
| payflow-kafka-1 | 48.3 | 208.32 | 768 |
| payflow-keycloak-1 | 45.5 | 659.78 | 3285 |
| payflow-mongo-1 | 36.6 | 145.26 | 384 |
| payflow-payflow-1 | 174.1 | 345.32 | 1357 |
| payflow-postgres-1 | 90.9 | 335.16 | 796 |
| payflow-postgres-exporter-1 | 0.4 | 3.62 | 15 |
| payflow-prometheus-1 | 2.3 | 50.76 | 117 |
| payflow-settlement-rail-1 | 2.0 | 49.15 | 46 |
| payflow-tempo-1 | 3.0 | 40.5 | 154 |
