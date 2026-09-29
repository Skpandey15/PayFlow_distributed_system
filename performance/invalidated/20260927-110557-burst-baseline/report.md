# Load-test report: 20260927-110557-burst-baseline

Window: 2026-09-27T05:35:59.802000+00:00 to 2026-09-27T05:42:19.821000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 16698 |
| iterations_per_s | 43.77999849905092 |
| dropped_iterations | 1 |
| payments_accepted | 8119 |
| payments_throttled | 0 |
| payments_rejected_at_api | 5184 |
| checks_pass_rate | 0.7028374892519347 |
| post_p50_ms | 5.889715 |
| post_p95_ms | 13.5480728 |
| post_p99_ms | 21.042932259999898 |
| post_max_ms | 477.724692 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 22.6667 |
| api_5xx_ratio | None |
| api_post_p50_ms | 6.6 |
| api_post_p95_ms | 15.0 |
| api_post_p99_ms | 23.9 |
| api_get_p99_ms | 4.8 |
| saga_completed | 451.0976 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 451.0976} |
| saga_p50_ms | 1771.2 |
| saga_p95_ms | 2375.8 |
| saga_p99_ms | 2749.4 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 785.7, "step=AWAITING_FUNDS": 909.7, "step=AWAITING_RISK": 135911.9, "step=AWAITING_SETTLEMENT": 712.1} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 7751.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 67206.2 |
| outbox_publish_delay_p99_ms | 179629.0 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 801.3, "outbox=payment.outbox_event": 179842.7, "outbox=settlement.outbox_event": 500.3} |
| outbox_send_p99_ms | 9.1 |
| outbox_published_per_s | 68.5973 |
| outbox_backlog_max | {"outbox=account.outbox_event": 6.0, "outbox=payment.outbox_event": 21434.0, "outbox=settlement.outbox_event": 0.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 167.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 0.0, "group=fraud-service,topic=fraud.commands": 0.0, "group=ledger-service,topic=funds.events": 0.0, "group=payment-service,topic=fraud.events": 0.0, "group=payment-service,topic=funds.events": 0.0} |
| consumer_p99_ms | {"consumer=ledger-service": 46.9, "consumer=payment-service": 49.5, "consumer=account-service": 54.1, "consumer=fraud-service": 32.4, "consumer=settlement-service": 72.7} |
| events_consumed_per_s | 14.76 |
| events_failed | {"category=CONCURRENCY": 0.0} |
| events_dead_lettered | 0 |
| hikari_active_max | 3.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 4.1 |
| hikari_acquire_avg_ms | 0.0 |
| hikari_usage_avg_ms | 5.3 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 257.7893 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 22.0 |
| mongo_cmd_max_ms | 109.3 |
| mongo_cmd_avg_ms | 0.7 |
| jvm_heap_used_max_mb | 174 |
| jvm_heap_after_gc_max_mb | 114 |
| jvm_heap_committed_max_mb | 223 |
| gc_pause_max_ms | 197.0 |
| gc_pause_total_ms | 1334.2 |
| gc_count | 234.5707 |
| alloc_rate_mb_s | 33.0 |
| threads_max | 189.0 |
| process_cpu_avg | 0.2315 |
| process_cpu_max | 0.435 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 2.15 | 320 |
| payflow-kafka-1 | 44.1 | 189.43 | 864 |
| payflow-keycloak-1 | 32.3 | 610.39 | 768 |
| payflow-mongo-1 | 12.2 | 99.78 | 475 |
| payflow-payflow-1 | 64.0 | 227.8 | 614 |
| payflow-postgres-1 | 14.3 | 43.74 | 488 |
| payflow-postgres-exporter-1 | 0.4 | 2.36 | 9 |
| payflow-prometheus-1 | 0.5 | 1.84 | 67 |
| payflow-tempo-1 | 0.5 | 2.17 | 938 |
