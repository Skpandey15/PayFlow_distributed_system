# Load-test report: 20260927-131736-normal-wp03

Window: 2026-09-27T07:47:39.316000+00:00 to 2026-09-27T07:57:39.365000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 12001 |
| iterations_per_s | 19.9417720941055 |
| dropped_iterations | 0 |
| payments_accepted | 12001 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 12.895716 |
| post_p95_ms | 32.44642 |
| post_p99_ms | 51.263678 |
| post_max_ms | 247.872203 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 20.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 12.0 |
| api_post_p95_ms | 31.0 |
| api_post_p99_ms | 49.2 |
| api_get_p99_ms | 9.0 |
| saga_completed | 12020.0492 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 12020.0492} |
| saga_p50_ms | 1634.3 |
| saga_p95_ms | 2002.5 |
| saga_p99_ms | 2124.6 |
| saga_step_p99_ms | {"step=AWAITING_SETTLEMENT": 724.4, "step=AWAITING_CAPTURE": 609.1, "step=AWAITING_FUNDS": 607.1, "step=AWAITING_RISK": 404.2} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 5.0, "step=AWAITING_FUNDS": 9.0, "step=AWAITING_RISK": 3.0, "step=AWAITING_SETTLEMENT": 16.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 14.0, "step=AWAITING_FUNDS": 11.0, "step=AWAITING_RISK": 7.0, "step=AWAITING_SETTLEMENT": 18.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 216.1 |
| outbox_publish_delay_p99_ms | 340.1 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 261.6, "outbox=payment.outbox_event": 342.8, "outbox=settlement.outbox_event": 346.8} |
| outbox_send_p99_ms | 22.4 |
| outbox_published_per_s | 219.8101 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 18.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=settlement.events": 6.0, "group=account-service,topic=funds.commands": 6.0, "group=payment-service,topic=funds.events": 6.0, "group=payment-service,topic=fraud.events": 5.0, "group=ledger-service,topic=funds.events": 4.0} |
| consumer_p99_ms | {"consumer=ledger-service": 38.1, "consumer=payment-service": 43.5, "consumer=account-service": 46.8, "consumer=fraud-service": 32.3, "consumer=settlement-service": 64.5} |
| events_consumed_per_s | 199.8336 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 12.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 8.0 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 6.9 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 563.4403 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 47.8 |
| mongo_cmd_avg_ms | 1.4 |
| jvm_heap_used_max_mb | 136 |
| jvm_heap_after_gc_max_mb | 85 |
| jvm_heap_committed_max_mb | 182 |
| gc_pause_max_ms | 191.0 |
| gc_pause_total_ms | 3638.8 |
| gc_count | 1065.6889 |
| alloc_rate_mb_s | 88.1 |
| threads_max | 74.0 |
| process_cpu_avg | 0.3984 |
| process_cpu_max | 0.587 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 1.76 | 320 |
| payflow-kafka-1 | 46.9 | 188.4 | 696 |
| payflow-keycloak-1 | 0.4 | 10.2 | 752 |
| payflow-mongo-1 | 13.4 | 55.32 | 363 |
| payflow-payflow-1 | 78.9 | 126.43 | 514 |
| payflow-postgres-1 | 40.1 | 110.44 | 368 |
| payflow-postgres-exporter-1 | 0.4 | 2.6 | 10 |
| payflow-prometheus-1 | 0.6 | 2.56 | 77 |
| payflow-settlement-rail-1 | 1.3 | 5.29 | 38 |
| payflow-tempo-1 | 1.1 | 4.6 | 175 |
