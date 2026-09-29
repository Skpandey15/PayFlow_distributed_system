# Load-test report: 20260929-130508-smoke-warmup-final

Window: 2026-09-29T07:35:12.919000+00:00 to 2026-09-29T07:36:42.932000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.795694843804681 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 15.117002 |
| post_p95_ms | 34.786815000000004 |
| post_p99_ms | 73.94106350000001 |
| post_max_ms | 99.062699 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 4.7558 |
| api_5xx_ratio | None |
| api_post_p50_ms | 14.0 |
| api_post_p95_ms | 30.1 |
| api_post_p99_ms | 71.6 |
| api_get_p99_ms | 13.9 |
| saga_completed | 451.5794 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 451.5794} |
| saga_p50_ms | 1621.4 |
| saga_p95_ms | 1862.7 |
| saga_p99_ms | 2127.9 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 594.4, "step=AWAITING_RISK": 346.6, "step=AWAITING_SETTLEMENT": 700.4, "step=AWAITING_CAPTURE": 589.9} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 2.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 4.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 228.3 |
| outbox_publish_delay_p99_ms | 338.4 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=settlement.outbox_event": 345.3, "outbox=account.outbox_event": 282.3, "outbox=payment.outbox_event": 340.5} |
| outbox_send_p99_ms | 29.9 |
| outbox_published_per_s | 52.5876 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 2.0, "group=settlement-service,topic=settlement.commands": 1.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=payment-service,topic=funds.events": 0.0, "group=ledger-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=ledger-service": 23.1, "consumer=payment-service": 33.7, "consumer=account-service": 44.3, "consumer=fraud-service": 61.0, "consumer=settlement-service": 65.9} |
| events_consumed_per_s | 47.7961 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 4.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 14.9 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 7.3 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 158.8824 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 95.4 |
| mongo_cmd_avg_ms | 1.7 |
| jvm_heap_used_max_mb | 178 |
| jvm_heap_after_gc_max_mb | 97 |
| jvm_heap_committed_max_mb | 192 |
| gc_pause_max_ms | 85.0 |
| gc_pause_total_ms | 306.5 |
| gc_count | 39.2417 |
| alloc_rate_mb_s | 27.7 |
| threads_max | 194.0 |
| process_cpu_avg | 0.3844 |
| process_cpu_max | 0.605 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.35 | 331 |
| payflow-kafka-1 | 53.4 | 172.0 | 572 |
| payflow-keycloak-1 | 0.2 | 0.27 | 736 |
| payflow-mongo-1 | 13.2 | 44.33 | 206 |
| payflow-payflow-1 | 75.7 | 125.65 | 541 |
| payflow-postgres-1 | 12.3 | 17.04 | 104 |
| payflow-postgres-exporter-1 | 0.0 | 0.0 | 9 |
| payflow-prometheus-1 | 0.4 | 0.73 | 71 |
| payflow-settlement-rail-1 | 1.2 | 2.06 | 30 |
| payflow-tempo-1 | 0.5 | 1.49 | 127 |
