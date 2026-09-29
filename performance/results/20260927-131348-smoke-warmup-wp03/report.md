# Load-test report: 20260927-131348-smoke-warmup-wp03

Window: 2026-09-27T07:43:54.090000+00:00 to 2026-09-27T07:45:24.118000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.814198540083584 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 17.108255 |
| post_p95_ms | 40.0798955 |
| post_p99_ms | 91.142268 |
| post_max_ms | 236.642272 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 15.7 |
| api_post_p95_ms | 37.4 |
| api_post_p99_ms | 81.3 |
| api_get_p99_ms | 13.4 |
| saga_completed | 441.4639 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 441.4639} |
| saga_p50_ms | 1618.9 |
| saga_p95_ms | 1788.2 |
| saga_p99_ms | 2070.4 |
| saga_step_p99_ms | {"step=AWAITING_SETTLEMENT": 658.6, "step=AWAITING_CAPTURE": 557.5, "step=AWAITING_FUNDS": 570.0, "step=AWAITING_RISK": 391.2} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 2.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 227.9 |
| outbox_publish_delay_p99_ms | 341.2 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=payment.outbox_event": 344.1, "outbox=settlement.outbox_event": 348.2, "outbox=account.outbox_event": 249.7} |
| outbox_send_p99_ms | 21.5 |
| outbox_published_per_s | 54.5166 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=settlement.events": 1.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=ledger-service": 23.3, "consumer=payment-service": 39.8, "consumer=account-service": 35.3, "consumer=fraud-service": 54.5, "consumer=settlement-service": 60.8} |
| events_consumed_per_s | 48.8143 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 2.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 8.8 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 7.7 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 158.6706 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 52.2 |
| mongo_cmd_avg_ms | 1.7 |
| jvm_heap_used_max_mb | 118 |
| jvm_heap_after_gc_max_mb | 73 |
| jvm_heap_committed_max_mb | 174 |
| gc_pause_max_ms | 159.0 |
| gc_pause_total_ms | 279.3 |
| gc_count | 62.5167 |
| alloc_rate_mb_s | 29.3 |
| threads_max | 70.0 |
| process_cpu_avg | 0.3773 |
| process_cpu_max | 0.805 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.5 | 4.86 | 320 |
| payflow-kafka-1 | 43.6 | 178.73 | 587 |
| payflow-keycloak-1 | 0.8 | 6.63 | 742 |
| payflow-mongo-1 | 12.5 | 53.89 | 370 |
| payflow-payflow-1 | 66.1 | 99.9 | 473 |
| payflow-postgres-1 | 12.9 | 18.88 | 152 |
| payflow-postgres-exporter-1 | 0.3 | 1.36 | 10 |
| payflow-prometheus-1 | 0.6 | 1.55 | 72 |
| payflow-settlement-rail-1 | 1.4 | 2.39 | 35 |
| payflow-tempo-1 | 0.9 | 5.06 | 168 |
