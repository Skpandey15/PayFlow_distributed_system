# Load-test report: 20260927-113506-burst-baseline

Window: 2026-09-27T06:05:08.880000+00:00 to 2026-09-27T06:11:28.922000+00:00 (+342s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 16699 |
| iterations_per_s | 43.74522478819804 |
| dropped_iterations | 0 |
| payments_accepted | 16691 |
| payments_throttled | 0 |
| payments_rejected_at_api | 7 |
| checks_pass_rate | 0.9997206703910615 |
| post_p50_ms | 7.1604115 |
| post_p95_ms | 41.49209209999999 |
| post_p99_ms | 458.5809867999955 |
| post_max_ms | 3930.274722 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 204.6933 |
| api_5xx_ratio | 0.0 |
| api_post_p50_ms | 7.1 |
| api_post_p95_ms | 38.6 |
| api_post_p99_ms | 348.7 |
| api_get_p99_ms | 136.5 |
| saga_completed | 1393.8611 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 1393.8611} |
| saga_p50_ms | 293698.4 |
| saga_p95_ms | 600000.0 |
| saga_p99_ms | 600000.0 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 539142.5, "step=AWAITING_FUNDS": 541150.7, "step=AWAITING_RISK": 544200.5, "step=AWAITING_SETTLEMENT": 540207.2} |
| drain_seconds_after_load | 342 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 1139.0, "step=AWAITING_FUNDS": 9553.0, "step=AWAITING_RISK": 64498.0, "step=AWAITING_SETTLEMENT": 2240.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 1522.0, "step=AWAITING_FUNDS": 21156.0, "step=AWAITING_RISK": 73124.0, "step=AWAITING_SETTLEMENT": 3631.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 1522.0, "step=AWAITING_FUNDS": 21156.0, "step=AWAITING_RISK": 83508.0, "step=AWAITING_SETTLEMENT": 3631.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 236885.9 |
| outbox_publish_delay_p99_ms | 543318.1 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 1073.5, "outbox=payment.outbox_event": 544154.7, "outbox=settlement.outbox_event": 1806.4} |
| outbox_send_p99_ms | 13.7 |
| outbox_published_per_s | 110.784 |
| outbox_backlog_max | {"outbox=account.outbox_event": 2.0, "outbox=payment.outbox_event": 190724.0, "outbox=settlement.outbox_event": 0.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 2.0, "outbox=payment.outbox_event": 540.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=fraud.events": 8.0, "group=account-service,topic=funds.commands": 4.0, "group=payment-service,topic=settlement.events": 1.0, "group=payment-service,topic=funds.events": 0.0, "group=ledger-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=ledger-service": 27.1, "consumer=payment-service": 45.5, "consumer=account-service": 81.4, "consumer=fraud-service": 40.5, "consumer=settlement-service": 207.1} |
| events_consumed_per_s | 113.0875 |
| events_failed | {"category=CONCURRENCY": 7.1156, "category=TRANSIENT_INFRASTRUCTURE": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 112.0 |
| hikari_acquire_max_ms | 3404.0 |
| hikari_acquire_avg_ms | 3.1 |
| hikari_usage_avg_ms | 4.3 |
| hikari_timeouts | 42.0639 |
| pg_commits_per_s | 2020.0187 |
| pg_rollbacks | 10.0448 |
| pg_deadlocks | 0 |
| pg_backends_max | 24.0 |
| mongo_cmd_max_ms | 87.0 |
| mongo_cmd_avg_ms | 1.3 |
| jvm_heap_used_max_mb | 233 |
| jvm_heap_after_gc_max_mb | 169 |
| jvm_heap_committed_max_mb | 242 |
| gc_pause_max_ms | 221.0 |
| gc_pause_total_ms | 7063.6 |
| gc_count | 1519.2083 |
| alloc_rate_mb_s | 178.5 |
| threads_max | 189.0 |
| process_cpu_avg | 0.6791 |
| process_cpu_max | 0.884 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 6.25 | 320 |
| payflow-kafka-1 | 65.9 | 193.91 | 670 |
| payflow-keycloak-1 | 19.0 | 401.3 | 2345 |
| payflow-mongo-1 | 15.3 | 66.68 | 374 |
| payflow-payflow-1 | 138.1 | 193.72 | 627 |
| payflow-postgres-1 | 62.2 | 126.03 | 513 |
| payflow-postgres-exporter-1 | 0.4 | 2.09 | 9 |
| payflow-prometheus-1 | 0.5 | 1.48 | 71 |
| payflow-tempo-1 | 2.0 | 11.35 | 998 |
