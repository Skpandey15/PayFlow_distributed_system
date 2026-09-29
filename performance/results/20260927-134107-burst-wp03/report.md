# Load-test report: 20260927-134107-burst-wp03

Window: 2026-09-27T08:11:10.289000+00:00 to 2026-09-27T08:17:30.312000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 16694 |
| iterations_per_s | 43.74275139479779 |
| dropped_iterations | 5 |
| payments_accepted | 16640 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 19.347451 |
| post_p95_ms | 206.69930574999987 |
| post_p99_ms | 666.7564499400002 |
| post_max_ms | 2547.310083 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 44.112 |
| api_5xx_ratio | None |
| api_post_p50_ms | 17.8 |
| api_post_p95_ms | 184.0 |
| api_post_p99_ms | 567.9 |
| api_get_p99_ms | 219.3 |
| saga_completed | 16827.6593 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 16827.6593} |
| saga_p50_ms | 56840.2 |
| saga_p95_ms | 109142.2 |
| saga_p99_ms | 126530.4 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 27126.6, "step=AWAITING_FUNDS": 41893.8, "step=AWAITING_RISK": 56998.7, "step=AWAITING_SETTLEMENT": 49829.7} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 14.0, "step=AWAITING_FUNDS": 10.0, "step=AWAITING_RISK": 5.0, "step=AWAITING_SETTLEMENT": 9.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 1489.0, "step=AWAITING_FUNDS": 1501.0, "step=AWAITING_RISK": 3088.0, "step=AWAITING_SETTLEMENT": 2712.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 155.0 |
| outbox_publish_delay_p99_ms | 505.7 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 395.4, "outbox=payment.outbox_event": 531.9, "outbox=settlement.outbox_event": 471.6} |
| outbox_send_p99_ms | 38.9 |
| outbox_published_per_s | 486.2587 |
| outbox_backlog_max | {"outbox=account.outbox_event": 4.0, "outbox=payment.outbox_event": 117.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 2928.0, "group=payment-service,topic=settlement.events": 2390.0, "group=payment-service,topic=funds.events": 1853.0, "group=fraud-service,topic=fraud.commands": 323.0, "group=account-service,topic=funds.commands": 195.0} |
| consumer_p99_ms | {"consumer=ledger-service": 45.2, "consumer=payment-service": 56.2, "consumer=account-service": 73.8, "consumer=fraud-service": 65.2, "consumer=settlement-service": 111.3} |
| events_consumed_per_s | 443.1799 |
| events_failed | {"category=CONCURRENCY": 3.042} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 32.0 |
| hikari_acquire_max_ms | 1522.5 |
| hikari_acquire_avg_ms | 1.0 |
| hikari_usage_avg_ms | 10.5 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1217.224 |
| pg_rollbacks | 4.0355 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 634.1 |
| mongo_cmd_avg_ms | 1.6 |
| jvm_heap_used_max_mb | 233 |
| jvm_heap_after_gc_max_mb | 182 |
| jvm_heap_committed_max_mb | 239 |
| gc_pause_max_ms | 114.0 |
| gc_pause_total_ms | 5908.3 |
| gc_count | 1381.163 |
| alloc_rate_mb_s | 182.2 |
| threads_max | 74.0 |
| process_cpu_avg | 0.6483 |
| process_cpu_max | 0.995 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.6 | 22.96 | 303 |
| payflow-kafka-1 | 124.7 | 308.7 | 906 |
| payflow-keycloak-1 | 9.4 | 278.15 | 2423 |
| payflow-mongo-1 | 25.2 | 113.52 | 385 |
| payflow-payflow-1 | 133.9 | 210.17 | 627 |
| payflow-postgres-1 | 84.2 | 225.66 | 690 |
| payflow-postgres-exporter-1 | 0.6 | 3.33 | 19 |
| payflow-prometheus-1 | 1.2 | 15.18 | 88 |
| payflow-settlement-rail-1 | 1.8 | 4.87 | 53 |
| payflow-tempo-1 | 2.2 | 14.45 | 132 |
