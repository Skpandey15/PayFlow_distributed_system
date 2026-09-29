# Load-test report: 20260929-110625-degraded-fault-F03-postgres-unavailable

Window: 2026-09-29T05:36:28.182000+00:00 to 2026-09-29T05:39:28.237000+00:00 (+30s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3569 |
| iterations_per_s | 19.6643131124531 |
| dropped_iterations | 32 |
| payments_accepted | 2738 |
| payments_throttled | 831 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.8318154219793564 |
| post_p50_ms | 14.415462 |
| post_p95_ms | 3005.7505428 |
| post_p99_ms | 3131.601912480005 |
| post_max_ms | 45390.953375 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 15.0686 |
| api_5xx_ratio | 0.2396 |
| api_post_p50_ms | 13.6 |
| api_post_p95_ms | 3141.6 |
| api_post_p99_ms | 3220.3 |
| api_get_p99_ms | 61.0 |
| saga_completed | 2802.7317 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 2802.7317} |
| saga_p50_ms | 1638.0 |
| saga_p95_ms | 2600.5 |
| saga_p99_ms | 46531.8 |
| saga_step_p99_ms | {"step=AWAITING_SETTLEMENT": 1314.7, "step=AWAITING_CAPTURE": 1011.0, "step=AWAITING_FUNDS": 1186.6, "step=AWAITING_RISK": 958.3} |
| drain_seconds_after_load | 30 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 9.0, "step=AWAITING_FUNDS": 10.0, "step=AWAITING_RISK": 4.0, "step=AWAITING_SETTLEMENT": 10.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 11.0, "step=AWAITING_FUNDS": 11.0, "step=AWAITING_RISK": 40.0, "step=AWAITING_SETTLEMENT": 15.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 215.9 |
| outbox_publish_delay_p99_ms | 340.5 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 278.0, "outbox=payment.outbox_event": 342.8, "outbox=settlement.outbox_event": 347.6} |
| outbox_send_p99_ms | 22.0 |
| outbox_published_per_s | 165.8857 |
| outbox_backlog_max | {"outbox=account.outbox_event": 2.0, "outbox=payment.outbox_event": 14.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=settlement.events": 5.0, "group=account-service,topic=funds.commands": 5.0, "group=settlement-service,topic=settlement.commands": 4.0, "group=payment-service,topic=fraud.events": 4.0, "group=payment-service,topic=funds.events": 1.0} |
| consumer_p99_ms | {"consumer=ledger-service": 32.4, "consumer=payment-service": 47.1, "consumer=account-service": 60.4, "consumer=fraud-service": 44.1, "consumer=settlement-service": 83.5} |
| events_consumed_per_s | 150.7029 |
| events_failed | {"category=TRANSIENT_INFRASTRUCTURE": 0.0, "category=UNKNOWN": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 49.0 |
| hikari_acquire_max_ms | 5006.1 |
| hikari_acquire_avg_ms | 52.8 |
| hikari_usage_avg_ms | 20.9 |
| hikari_timeouts | 682.1648 |
| pg_commits_per_s | 426.36 |
| pg_rollbacks | 4.0976 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 56.6 |
| mongo_cmd_avg_ms | 1.5 |
| jvm_heap_used_max_mb | 214 |
| jvm_heap_after_gc_max_mb | 81 |
| jvm_heap_committed_max_mb | 234 |
| gc_pause_max_ms | 14.0 |
| gc_pause_total_ms | 437.4 |
| gc_count | 96.2927 |
| alloc_rate_mb_s | 69.2 |
| threads_max | 74.0 |
| process_cpu_avg | 0.359 |
| process_cpu_max | 0.757 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.5 | 5.85 | 325 |
| payflow-kafka-1 | 45.2 | 160.48 | 607 |
| payflow-keycloak-1 | 2.9 | 37.3 | 770 |
| payflow-mongo-1 | 12.2 | 48.42 | 389 |
| payflow-payflow-1 | 64.4 | 106.19 | 595 |
| payflow-postgres-1 | 32.4 | 93.01 | 702 |
| payflow-postgres-exporter-1 | 0.2 | 1.61 | 12 |
| payflow-prometheus-1 | 0.6 | 1.04 | 70 |
| payflow-settlement-rail-1 | 0.5 | 0.97 | 42 |
| payflow-tempo-1 | 1.3 | 4.82 | 1073 |
