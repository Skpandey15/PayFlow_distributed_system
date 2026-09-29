# Load-test report: 20260929-125044-stress-p2tuned

Window: 2026-09-29T07:20:48.863000+00:00 to 2026-09-29T07:30:48.945000+00:00 (+31s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 113098 |
| iterations_per_s | 187.2264325909734 |
| dropped_iterations | 2 |
| payments_accepted | 31980 |
| payments_throttled | 81118 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.3715388071958721 |
| post_p50_ms | 2.4944309999999996 |
| post_p95_ms | 81.64592194999994 |
| post_p99_ms | 178.89556655999996 |
| post_max_ms | 513.541365 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 53.5748 |
| api_5xx_ratio | 0.718 |
| api_post_p50_ms | 0.8 |
| api_post_p95_ms | 56.4 |
| api_post_p99_ms | 128.6 |
| api_get_p99_ms | 96.0 |
| saga_completed | 32030.7619 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 32030.7619} |
| saga_p50_ms | 40306.2 |
| saga_p95_ms | 55931.0 |
| saga_p99_ms | 61851.4 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 21860.0, "step=AWAITING_FUNDS": 21819.3, "step=AWAITING_RISK": 22525.9, "step=AWAITING_SETTLEMENT": 21304.2} |
| drain_seconds_after_load | 31 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 549.0, "step=AWAITING_FUNDS": 853.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 191.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 1584.0, "step=AWAITING_FUNDS": 1579.0, "step=AWAITING_RISK": 2028.0, "step=AWAITING_SETTLEMENT": 1230.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 157.4 |
| outbox_publish_delay_p99_ms | 593.1 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 545.1, "outbox=payment.outbox_event": 593.5, "outbox=settlement.outbox_event": 654.6} |
| outbox_send_p99_ms | 92.5 |
| outbox_published_per_s | 584.4739 |
| outbox_backlog_max | {"outbox=account.outbox_event": 9.0, "outbox=payment.outbox_event": 295.0, "outbox=settlement.outbox_event": 4.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 2041.0, "group=fraud-service,topic=fraud.commands": 1922.0, "group=payment-service,topic=fraud.events": 1706.0, "group=payment-service,topic=settlement.events": 1026.0, "group=account-service,topic=funds.commands": 507.0} |
| consumer_p99_ms | {"consumer=payment-service": 60.3, "consumer=account-service": 72.2, "consumer=fraud-service": 98.4, "consumer=settlement-service": 116.6, "consumer=ledger-service": 50.7} |
| events_consumed_per_s | 529.7059 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 19.0 |
| hikari_pending_max | 5.0 |
| hikari_acquire_max_ms | 314.3 |
| hikari_acquire_avg_ms | 0.6 |
| hikari_usage_avg_ms | 9.2 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1462.5378 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 111.0 |
| mongo_cmd_avg_ms | 1.5 |
| jvm_heap_used_max_mb | 233 |
| jvm_heap_after_gc_max_mb | 106 |
| jvm_heap_committed_max_mb | 257 |
| gc_pause_max_ms | 54.0 |
| gc_pause_total_ms | 4747.5 |
| gc_count | 948.5032 |
| alloc_rate_mb_s | 205.9 |
| threads_max | 198.0 |
| process_cpu_avg | 0.8538 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.0 | 1.82 | 331 |
| payflow-kafka-1 | 71.5 | 205.14 | 751 |
| payflow-keycloak-1 | 2.9 | 75.97 | 713 |
| payflow-mongo-1 | 18.3 | 90.58 | 377 |
| payflow-payflow-1 | 174.1 | 209.53 | 628 |
| payflow-postgres-1 | 79.0 | 152.03 | 611 |
| payflow-postgres-exporter-1 | 0.4 | 2.51 | 9 |
| payflow-prometheus-1 | 0.6 | 1.28 | 80 |
| payflow-settlement-rail-1 | 3.1 | 27.41 | 47 |
| payflow-tempo-1 | 2.5 | 14.56 | 159 |
