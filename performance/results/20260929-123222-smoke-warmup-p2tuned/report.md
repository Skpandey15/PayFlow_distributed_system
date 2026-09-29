# Load-test report: 20260929-123222-smoke-warmup-p2tuned

Window: 2026-09-29T07:02:28.376000+00:00 to 2026-09-29T07:03:58.401000+00:00 (+30s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 451 |
| iterations_per_s | 4.769104522550046 |
| dropped_iterations | 0 |
| payments_accepted | 451 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 18.933932 |
| post_p95_ms | 48.8663875 |
| post_p99_ms | 90.4507295 |
| post_max_ms | 111.727724 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 4.7819 |
| api_5xx_ratio | None |
| api_post_p50_ms | 17.5 |
| api_post_p95_ms | 42.7 |
| api_post_p99_ms | 67.1 |
| api_get_p99_ms | 28.5 |
| saga_completed | 450.5782 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 450.5782} |
| saga_p50_ms | 1650.5 |
| saga_p95_ms | 2051.7 |
| saga_p99_ms | 2128.3 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 608.2, "step=AWAITING_FUNDS": 611.3, "step=AWAITING_RISK": 353.8, "step=AWAITING_SETTLEMENT": 705.4} |
| drain_seconds_after_load | 30 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 1.0, "step=AWAITING_FUNDS": 1.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 4.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 3.0, "step=AWAITING_FUNDS": 3.0, "step=AWAITING_RISK": 2.0, "step=AWAITING_SETTLEMENT": 4.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 234.5 |
| outbox_publish_delay_p99_ms | 350.3 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 263.7, "outbox=payment.outbox_event": 351.5, "outbox=settlement.outbox_event": 352.7} |
| outbox_send_p99_ms | 28.4 |
| outbox_published_per_s | 52.6964 |
| outbox_backlog_max | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 7.0, "outbox=settlement.outbox_event": 1.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 0.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=settlement-service": 80.9, "consumer=ledger-service": 31.6, "consumer=payment-service": 42.2, "consumer=account-service": 57.4, "consumer=fraud-service": 57.4} |
| events_consumed_per_s | 47.9743 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 4.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 193.6 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 9.4 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 158.6824 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 42.1 |
| mongo_cmd_avg_ms | 2.0 |
| jvm_heap_used_max_mb | 189 |
| jvm_heap_after_gc_max_mb | 97 |
| jvm_heap_committed_max_mb | 223 |
| gc_pause_max_ms | 28.0 |
| gc_pause_total_ms | 355.8 |
| gc_count | 27.5903 |
| alloc_rate_mb_s | 29.8 |
| threads_max | 193.0 |
| process_cpu_avg | 0.4449 |
| process_cpu_max | 0.664 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.4 | 2.05 | 325 |
| payflow-kafka-1 | 52.9 | 136.36 | 564 |
| payflow-keycloak-1 | 0.3 | 0.37 | 776 |
| payflow-mongo-1 | 11.9 | 50.08 | 206 |
| payflow-payflow-1 | 85.2 | 117.43 | 578 |
| payflow-postgres-1 | 15.8 | 20.01 | 108 |
| payflow-postgres-exporter-1 | 0.3 | 1.9 | 8 |
| payflow-prometheus-1 | 0.4 | 0.64 | 108 |
| payflow-settlement-rail-1 | 2.9 | 10.77 | 36 |
| payflow-tempo-1 | 1.0 | 2.78 | 121 |
