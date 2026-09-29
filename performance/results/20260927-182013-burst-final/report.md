# Load-test report: 20260927-182013-burst-final

Window: 2026-09-27T12:50:16.186000+00:00 to 2026-09-27T12:56:36.214000+00:00 (+30s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 16699 |
| iterations_per_s | 43.74137877570799 |
| dropped_iterations | 0 |
| payments_accepted | 16671 |
| payments_throttled | 27 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.9989205181512874 |
| post_p50_ms | 25.4147555 |
| post_p95_ms | 187.83736184999992 |
| post_p99_ms | 321.2195531699997 |
| post_max_ms | 910.715752 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 44.1973 |
| api_5xx_ratio | 0.0 |
| api_post_p50_ms | 20.9 |
| api_post_p95_ms | 124.4 |
| api_post_p99_ms | 267.6 |
| api_get_p99_ms | 112.0 |
| saga_completed | 16841.3827 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 16841.3827} |
| saga_p50_ms | 74869.3 |
| saga_p95_ms | 128726.2 |
| saga_p99_ms | 135957.2 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 44951.3, "step=AWAITING_RISK": 65539.4, "step=AWAITING_SETTLEMENT": 48872.8, "step=AWAITING_CAPTURE": 45364.0} |
| drain_seconds_after_load | 30 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 6.0, "step=AWAITING_FUNDS": 9.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 12.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 1662.0, "step=AWAITING_FUNDS": 2543.0, "step=AWAITING_RISK": 3795.0, "step=AWAITING_SETTLEMENT": 2131.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 165.7 |
| outbox_publish_delay_p99_ms | 600.1 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=payment.outbox_event": 626.6, "outbox=settlement.outbox_event": 524.4, "outbox=account.outbox_event": 472.5} |
| outbox_send_p99_ms | 64.7 |
| outbox_published_per_s | 488.5627 |
| outbox_backlog_max | {"outbox=account.outbox_event": 6.0, "outbox=payment.outbox_event": 78.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 3217.0, "group=payment-service,topic=fraud.events": 2474.0, "group=fraud-service,topic=fraud.commands": 2410.0, "group=payment-service,topic=settlement.events": 2331.0, "group=account-service,topic=funds.commands": 284.0} |
| consumer_p99_ms | {"consumer=ledger-service": 43.6, "consumer=payment-service": 57.1, "consumer=account-service": 72.2, "consumer=fraud-service": 83.4, "consumer=settlement-service": 111.6} |
| events_consumed_per_s | 445.9546 |
| events_failed | {"category=CONCURRENCY": 3.0428} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 22.0 |
| hikari_acquire_max_ms | 392.6 |
| hikari_acquire_avg_ms | 0.4 |
| hikari_usage_avg_ms | 9.6 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1221.8667 |
| pg_rollbacks | 5.0431 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 109.5 |
| mongo_cmd_avg_ms | 1.6 |
| jvm_heap_used_max_mb | 240 |
| jvm_heap_after_gc_max_mb | 91 |
| jvm_heap_committed_max_mb | 271 |
| gc_pause_max_ms | 42.0 |
| gc_pause_total_ms | 1899.2 |
| gc_count | 496.0494 |
| alloc_rate_mb_s | 185.5 |
| threads_max | 73.0 |
| process_cpu_avg | 0.6933 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 1.43 | 332 |
| payflow-kafka-1 | 64.5 | 137.4 | 543 |
| payflow-keycloak-1 | 0.3 | 0.29 | 752 |
| payflow-mongo-1 | 17.5 | 55.84 | 207 |
| payflow-payflow-1 | 118.1 | 125.13 | 498 |
| payflow-postgres-1 | 38.1 | 44.92 | 137 |
| payflow-postgres-exporter-1 | 0.5 | 1.43 | 9 |
| payflow-prometheus-1 | 0.8 | 1.06 | 63 |
| payflow-settlement-rail-1 | 2.8 | 4.45 | 32 |
| payflow-tempo-1 | 0.6 | 1.04 | 509 |
