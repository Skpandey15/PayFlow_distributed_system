# Load-test report: 20260927-172838-normal-final

Window: 2026-09-27T11:58:41.117000+00:00 to 2026-09-27T12:08:41.151000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 12001 |
| iterations_per_s | 19.945454357082706 |
| dropped_iterations | 0 |
| payments_accepted | 12001 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 12.786983 |
| post_p95_ms | 30.583175 |
| post_p99_ms | 49.182262 |
| post_max_ms | 324.964653 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 20.0017 |
| api_5xx_ratio | None |
| api_post_p50_ms | 11.7 |
| api_post_p95_ms | 28.8 |
| api_post_p99_ms | 47.9 |
| api_get_p99_ms | 8.3 |
| saga_completed | 12074.816 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 12074.816} |
| saga_p50_ms | 1631.1 |
| saga_p95_ms | 1982.4 |
| saga_p99_ms | 2116.3 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 603.2, "step=AWAITING_FUNDS": 607.3, "step=AWAITING_RISK": 394.0, "step=AWAITING_SETTLEMENT": 715.4} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 10.0, "step=AWAITING_FUNDS": 10.0, "step=AWAITING_RISK": 3.0, "step=AWAITING_SETTLEMENT": 10.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 14.0, "step=AWAITING_FUNDS": 15.0, "step=AWAITING_RISK": 7.0, "step=AWAITING_SETTLEMENT": 16.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 219.0 |
| outbox_publish_delay_p99_ms | 342.8 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 261.7, "outbox=payment.outbox_event": 345.6, "outbox=settlement.outbox_event": 346.3} |
| outbox_send_p99_ms | 25.8 |
| outbox_published_per_s | 220.0067 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 16.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 6.0, "group=payment-service,topic=settlement.events": 5.0, "group=fraud-service,topic=fraud.commands": 4.0, "group=payment-service,topic=funds.events": 4.0, "group=account-service,topic=funds.commands": 4.0} |
| consumer_p99_ms | {"consumer=fraud-service": 30.6, "consumer=settlement-service": 63.0, "consumer=ledger-service": 33.6, "consumer=payment-service": 41.7, "consumer=account-service": 45.5} |
| events_consumed_per_s | 200.037 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 12.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 9.2 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 6.4 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 563.1345 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 48.8 |
| mongo_cmd_avg_ms | 1.3 |
| jvm_heap_used_max_mb | 199 |
| jvm_heap_after_gc_max_mb | 74 |
| jvm_heap_committed_max_mb | 212 |
| gc_pause_max_ms | 22.0 |
| gc_pause_total_ms | 1392.2 |
| gc_count | 369.5136 |
| alloc_rate_mb_s | 75.8 |
| threads_max | 72.0 |
| process_cpu_avg | 0.3752 |
| process_cpu_max | 0.649 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.16 | 333 |
| payflow-kafka-1 | 65.4 | 179.39 | 586 |
| payflow-keycloak-1 | 0.2 | 0.26 | 753 |
| payflow-mongo-1 | 15.2 | 47.4 | 208 |
| payflow-payflow-1 | 90.1 | 100.07 | 586 |
| payflow-postgres-1 | 33.1 | 40.3 | 133 |
| payflow-postgres-exporter-1 | 0.0 | 0.0 | 9 |
| payflow-prometheus-1 | 0.4 | 0.88 | 64 |
| payflow-settlement-rail-1 | 4.0 | 8.29 | 33 |
| payflow-tempo-1 | 0.7 | 1.8 | 57 |
