# Load-test report: 20260930-081106-burst-window400-wsl

Window: 2026-09-30T08:11:11.215000+00:00 to 2026-09-30T08:17:44.582000+00:00 (+32s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 16699 |
| iterations_per_s | 42.07322226597997 |
| dropped_iterations | 0 |
| payments_accepted | 9524 |
| payments_throttled | 7036 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.6697953820161442 |
| post_p50_ms | 10.292576 |
| post_p95_ms | 87.53722495 |
| post_p99_ms | 776.0013155299989 |
| post_max_ms | 2982.029392 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 24.3423 |
| api_5xx_ratio | 0.4101 |
| api_post_p50_ms | 8.2 |
| api_post_p95_ms | 64.4 |
| api_post_p99_ms | 651.2 |
| api_get_p99_ms | 40.7 |
| saga_completed | 9632.5285 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 9632.5285} |
| saga_p50_ms | 2327.4 |
| saga_p95_ms | 17785.3 |
| saga_p99_ms | 22015.7 |
| saga_step_p99_ms | {"step=AWAITING_FUNDS": 6818.7, "step=AWAITING_SETTLEMENT": 6695.9, "step=AWAITING_RISK": 6855.7, "step=AWAITING_CAPTURE": 6758.6} |
| drain_seconds_after_load | 32 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 12.0, "step=AWAITING_FUNDS": 10.0, "step=AWAITING_RISK": 7.0, "step=AWAITING_SETTLEMENT": 8.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 228.0, "step=AWAITING_FUNDS": 173.0, "step=AWAITING_RISK": 226.0, "step=AWAITING_SETTLEMENT": 171.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 205.4 |
| outbox_publish_delay_p99_ms | 1184.9 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 752.0, "outbox=payment.outbox_event": 1228.6, "outbox=settlement.outbox_event": 1302.1} |
| outbox_send_p99_ms | 50.9 |
| outbox_published_per_s | 268.9729 |
| outbox_backlog_max | {"outbox=account.outbox_event": 2.0, "outbox=payment.outbox_event": 65.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=payment-service,topic=funds.events": 317.0, "group=payment-service,topic=fraud.events": 146.0, "group=payment-service,topic=settlement.events": 140.0, "group=fraud-service,topic=fraud.commands": 82.0, "group=account-service,topic=funds.commands": 47.0} |
| consumer_p99_ms | {"consumer=settlement-service": 113.9, "consumer=fraud-service": 82.5, "consumer=ledger-service": 47.4, "consumer=payment-service": 55.0, "consumer=account-service": 66.7} |
| events_consumed_per_s | 244.6802 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 38.0 |
| hikari_acquire_max_ms | 2594.3 |
| hikari_acquire_avg_ms | 0.9 |
| hikari_usage_avg_ms | 11.5 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 692.1462 |
| pg_rollbacks | 0.0 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 2946.6 |
| mongo_cmd_avg_ms | 2.4 |
| jvm_heap_used_max_mb | 379 |
| jvm_heap_after_gc_max_mb | 110 |
| jvm_heap_committed_max_mb | 475 |
| gc_pause_max_ms | 760.0 |
| gc_pause_total_ms | 3102.1 |
| gc_count | 168.1211 |
| alloc_rate_mb_s | 109.5 |
| threads_max | 198.0 |
| process_cpu_avg | 0.4545 |
| process_cpu_max | 0.949 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.5 | 11.24 | 188 |
| payflow-kafka-1 | 52.5 | 187.92 | 424 |
| payflow-keycloak-1 | 11.8 | 206.49 | 1135 |
| payflow-mongo-1 | 14.6 | 54.33 | 289 |
| payflow-payflow-1 | 99.7 | 205.16 | 699 |
| payflow-postgres-1 | 43.5 | 86.22 | 235 |
| payflow-postgres-exporter-1 | 0.4 | 1.97 | 13 |
| payflow-prometheus-1 | 0.8 | 5.12 | 72 |
| payflow-settlement-rail-1 | 1.9 | 8.35 | 38 |
| payflow-tempo-1 | 1.4 | 8.9 | 581 |
