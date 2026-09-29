# Load-test report: 20260927-160136-smoke-warmup-final

Window: 2026-09-27T10:32:58.884000+00:00 to 2026-09-27T10:34:28.898000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.830903591621153 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 17.011587 |
| post_p95_ms | 30.2969015 |
| post_p99_ms | 55.259671499999996 |
| post_max_ms | 278.444102 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 5.0 |
| api_5xx_ratio | None |
| api_post_p50_ms | 15.8 |
| api_post_p95_ms | 28.6 |
| api_post_p99_ms | 46.5 |
| api_get_p99_ms | 9.3 |
| saga_completed | 454.7583 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 454.7583} |
| saga_p50_ms | 1617.4 |
| saga_p95_ms | 1785.3 |
| saga_p99_ms | 2120.4 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 581.4, "step=AWAITING_FUNDS": 551.5, "step=AWAITING_RISK": 336.9, "step=AWAITING_SETTLEMENT": 648.3} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 2.0, "step=AWAITING_FUNDS": 2.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 1.0, "step=AWAITING_SETTLEMENT": 3.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 227.9 |
| outbox_publish_delay_p99_ms | 334.9 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 246.1, "outbox=payment.outbox_event": 338.1, "outbox=settlement.outbox_event": 346.7} |
| outbox_send_p99_ms | 20.3 |
| outbox_published_per_s | 54.8706 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 6.0, "outbox=settlement.outbox_event": 2.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 0.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=ledger-service": 22.4, "consumer=payment-service": 31.7, "consumer=account-service": 55.9, "consumer=fraud-service": 48.4, "consumer=settlement-service": 70.8} |
| events_consumed_per_s | 49.9321 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 4.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 19.3 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 7.2 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 159.6118 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 64.7 |
| mongo_cmd_avg_ms | 1.6 |
| jvm_heap_used_max_mb | 213 |
| jvm_heap_after_gc_max_mb | 73 |
| jvm_heap_committed_max_mb | 237 |
| gc_pause_max_ms | 62.0 |
| gc_pause_total_ms | 249.1 |
| gc_count | 23.1917 |
| alloc_rate_mb_s | 29.3 |
| threads_max | 72.0 |
| process_cpu_avg | 0.3364 |
| process_cpu_max | 0.57 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 1.62 | 170 |
| payflow-kafka-1 | 37.9 | 176.51 | 847 |
| payflow-keycloak-1 | 0.2 | 0.28 | 4326 |
| payflow-mongo-1 | 5.9 | 31.92 | 272 |
| payflow-payflow-1 | 67.6 | 104.41 | 627 |
| payflow-postgres-1 | 13.4 | 17.43 | 613 |
| payflow-postgres-exporter-1 | 0.4 | 1.91 | 13 |
| payflow-prometheus-1 | 0.7 | 2.37 | 86 |
| payflow-settlement-rail-1 | 0.2 | 0.3 | 123 |
| payflow-tempo-1 | 0.6 | 1.57 | 133 |
