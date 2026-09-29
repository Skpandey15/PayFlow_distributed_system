# Load-test report: 20260929-112224-degraded-fault-F21-graceful-shutdown-sigterm

Window: 2026-09-29T05:52:27.345000+00:00 to 2026-09-29T05:55:27.491000+00:00 (+30s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3456 |
| iterations_per_s | 19.0256994112939 |
| dropped_iterations | 145 |
| payments_accepted | 3085 |
| payments_throttled | 0 |
| payments_rejected_at_api | 371 |
| checks_pass_rate | 0.9258148370325935 |
| post_p50_ms | 33.437145 |
| post_p95_ms | 313.01234550000004 |
| post_p99_ms | 761.2357824499966 |
| post_max_ms | 1707.649074 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 17.0114 |
| api_5xx_ratio | None |
| api_post_p50_ms | 28.6 |
| api_post_p95_ms | 206.7 |
| api_post_p99_ms | 575.9 |
| api_get_p99_ms | 127.2 |
| saga_completed | 3140.7805 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3140.7805} |
| saga_p50_ms | 19139.0 |
| saga_p95_ms | 48971.8 |
| saga_p99_ms | 54768.4 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 16075.2, "step=AWAITING_FUNDS": 14144.5, "step=AWAITING_RISK": 22634.8, "step=AWAITING_SETTLEMENT": 14031.8} |
| drain_seconds_after_load | 30 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 123.0, "step=AWAITING_FUNDS": 116.0, "step=AWAITING_RISK": 90.0, "step=AWAITING_SETTLEMENT": 171.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 267.0, "step=AWAITING_FUNDS": 292.0, "step=AWAITING_RISK": 353.0, "step=AWAITING_SETTLEMENT": 209.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 218.1 |
| outbox_publish_delay_p99_ms | 5091.6 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 1050.7, "outbox=payment.outbox_event": 6974.5, "outbox=settlement.outbox_event": 859.6} |
| outbox_send_p99_ms | 203.0 |
| outbox_published_per_s | 179.0343 |
| outbox_backlog_max | {"outbox=account.outbox_event": 10.0, "outbox=payment.outbox_event": 24.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 366.0, "group=payment-service,topic=fraud.events": 282.0, "group=payment-service,topic=settlement.events": 256.0, "group=fraud-service,topic=fraud.commands": 247.0, "group=settlement-service,topic=settlement.commands": 30.0} |
| consumer_p99_ms | {"consumer=ledger-service": 86.3, "consumer=payment-service": 103.5, "consumer=account-service": 115.1, "consumer=fraud-service": 299.3, "consumer=settlement-service": 308.0} |
| events_consumed_per_s | 161.8571 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 10.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 290.9 |
| hikari_acquire_avg_ms | 0.1 |
| hikari_usage_avg_ms | 17.2 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 452.8686 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 113.4 |
| mongo_cmd_avg_ms | 4.1 |
| jvm_heap_used_max_mb | 208 |
| jvm_heap_after_gc_max_mb | 78 |
| jvm_heap_committed_max_mb | 230 |
| gc_pause_max_ms | 61.0 |
| gc_pause_total_ms | 1138.1 |
| gc_count | 113.7073 |
| alloc_rate_mb_s | 67.9 |
| threads_max | 71.0 |
| process_cpu_avg | 0.7818 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 1.47 | 325 |
| payflow-kafka-1 | 42.2 | 188.37 | 732 |
| payflow-keycloak-1 | 6.5 | 41.81 | 780 |
| payflow-mongo-1 | 10.8 | 52.91 | 375 |
| payflow-payflow-1 | 159.3 | 205.82 | 579 |
| payflow-postgres-1 | 33.4 | 71.62 | 724 |
| payflow-postgres-exporter-1 | 0.3 | 2.08 | 10 |
| payflow-prometheus-1 | 0.5 | 1.36 | 74 |
| payflow-settlement-rail-1 | 0.9 | 2.97 | 45 |
| payflow-tempo-1 | 1.1 | 3.16 | 162 |
