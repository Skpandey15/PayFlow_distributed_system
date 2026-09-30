# Load-test report: 20260929-185314-degraded-fault-F07-settlement-slow-bulkhead

Window: 2026-09-29T18:53:23.218000+00:00 to 2026-09-29T18:56:26.168000+00:00 (+336s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3601 |
| iterations_per_s | 19.225995989155507 |
| dropped_iterations | 0 |
| payments_accepted | 3601 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 17.8006 |
| post_p95_ms | 207.354265 |
| post_p99_ms | 450.261949 |
| post_max_ms | 1040.964561 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 19.6616 |
| api_5xx_ratio | None |
| api_post_p50_ms | 15.9 |
| api_post_p95_ms | 125.8 |
| api_post_p99_ms | 241.6 |
| api_get_p99_ms | 81.7 |
| saga_completed | 3602.5936 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3602.5936} |
| saga_p50_ms | 26154.4 |
| saga_p95_ms | 337510.5 |
| saga_p99_ms | 360705.2 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 8373.1, "step=AWAITING_CAPTURE": 7105.8, "step=AWAITING_FUNDS": 7485.4, "step=AWAITING_SETTLEMENT": 40858.7} |
| drain_seconds_after_load | 336 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 5.0, "step=AWAITING_FUNDS": 8.0, "step=AWAITING_RISK": 6.0, "step=AWAITING_SETTLEMENT": 1533.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 123.0, "step=AWAITING_FUNDS": 110.0, "step=AWAITING_RISK": 111.0, "step=AWAITING_SETTLEMENT": 2026.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 202.8 |
| outbox_publish_delay_p99_ms | 907.7 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=settlement.outbox_event": 986.3, "outbox=account.outbox_event": 839.5, "outbox=payment.outbox_event": 917.9} |
| outbox_send_p99_ms | 79.5 |
| outbox_published_per_s | 182.5465 |
| outbox_backlog_max | {"outbox=account.outbox_event": 3.0, "outbox=payment.outbox_event": 37.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=settlement-service,topic=settlement.commands": 546.0, "group=settlement-service-settlement-service-retry-2,topic=settlement.commands-settlement-service-retry-2": 415.0, "group=settlement-service-settlement-service-retry-1,topic=settlement.commands-settlement-service-retry-1": 257.0, "group=settlement-service-settlement-service-retry-0,topic=settlement.commands-settlement-service-retry-0": 220.0, "group=payment-service,topic=funds.events": 175.0} |
| consumer_p99_ms | {"consumer=payment-service": 88.9, "consumer=account-service": 110.3, "consumer=settlement-service": 1803.6, "consumer=fraud-service": 237.9, "consumer=ledger-service": 72.1} |
| events_consumed_per_s | 154.1813 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 7203.6296} |
| events_dead_lettered | 1519.7909 |
| hikari_active_max | 12.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 66.4 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 9.4 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 620.0224 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 82.8 |
| mongo_cmd_avg_ms | 2.8 |
| jvm_heap_used_max_mb | 250 |
| jvm_heap_after_gc_max_mb | 103 |
| jvm_heap_committed_max_mb | 267 |
| gc_pause_max_ms | 54.0 |
| gc_pause_total_ms | 1271.0 |
| gc_count | 176.622 |
| alloc_rate_mb_s | 107.5 |
| threads_max | 195.0 |
| process_cpu_avg | 0.6993 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 2.04 | 271 |
| payflow-kafka-1 | 67.1 | 195.22 | 654 |
| payflow-keycloak-1 | 0.2 | 0.34 | 689 |
| payflow-mongo-1 | 16.7 | 61.96 | 255 |
| payflow-payflow-1 | 145.1 | 207.37 | 678 |
| payflow-postgres-1 | 35.8 | 56.73 | 188 |
| payflow-postgres-exporter-1 | 0.3 | 1.42 | 18 |
| payflow-prometheus-1 | 0.5 | 1.58 | 66 |
| payflow-settlement-rail-1 | 2.2 | 6.92 | 58 |
| payflow-tempo-1 | 1.1 | 2.92 | 54 |
