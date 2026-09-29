# Load-test report: 20260929-213618-degraded-fault-F07-settlement-slow-bulkhead

Window: 2026-09-29T16:06:44.280000+00:00 to 2026-09-29T16:09:46.140000+00:00 (+204s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 3581 |
| iterations_per_s | 18.88409056380222 |
| dropped_iterations | 20 |
| payments_accepted | 3581 |
| payments_throttled | 0 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 1 |
| post_p50_ms | 23.79261 |
| post_p95_ms | 439.354126 |
| post_p99_ms | 952.901460399998 |
| post_max_ms | 2355.763922 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 19.8229 |
| api_5xx_ratio | None |
| api_post_p50_ms | 21.6 |
| api_post_p95_ms | 279.2 |
| api_post_p99_ms | 783.7 |
| api_get_p99_ms | 244.6 |
| saga_completed | 3583.9806 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 3583.9806} |
| saga_p50_ms | 138253.0 |
| saga_p95_ms | 220830.4 |
| saga_p99_ms | 228154.3 |
| saga_step_p99_ms | {"step=AWAITING_RISK": 20023.5, "step=AWAITING_SETTLEMENT": 224456.6, "step=AWAITING_CAPTURE": 13407.5, "step=AWAITING_FUNDS": 10376.6} |
| drain_seconds_after_load | 204 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 31.0, "step=AWAITING_FUNDS": 17.0, "step=AWAITING_RISK": 297.0, "step=AWAITING_SETTLEMENT": 1892.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 160.0, "step=AWAITING_FUNDS": 165.0, "step=AWAITING_RISK": 297.0, "step=AWAITING_SETTLEMENT": 2091.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 208.3 |
| outbox_publish_delay_p99_ms | 7660.7 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 2076.0, "outbox=payment.outbox_event": 9840.3, "outbox=settlement.outbox_event": 3096.8} |
| outbox_send_p99_ms | 64.2 |
| outbox_published_per_s | 158.4362 |
| outbox_backlog_max | {"outbox=account.outbox_event": 12.0, "outbox=payment.outbox_event": 410.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 5.0, "outbox=payment.outbox_event": 10.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=settlement-service,topic=settlement.commands": 657.0, "group=payment-service,topic=funds.events": 253.0, "group=payment-service,topic=fraud.events": 238.0, "group=payment-service,topic=settlement.events": 154.0, "group=fraud-service,topic=fraud.commands": 78.0} |
| consumer_p99_ms | {"consumer=account-service": 193.9, "consumer=fraud-service": 127.3, "consumer=settlement-service": 1589.8, "consumer=ledger-service": 152.0, "consumer=payment-service": 192.8} |
| events_consumed_per_s | 140.0648 |
| events_failed | {} |
| events_dead_lettered | 0 |
| hikari_active_max | 11.0 |
| hikari_pending_max | 0.0 |
| hikari_acquire_max_ms | 498.1 |
| hikari_acquire_avg_ms | 0.2 |
| hikari_usage_avg_ms | 18.2 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 461.2971 |
| pg_rollbacks | 1.0132 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 470.3 |
| mongo_cmd_avg_ms | 2.6 |
| jvm_heap_used_max_mb | 307 |
| jvm_heap_after_gc_max_mb | 106 |
| jvm_heap_committed_max_mb | 352 |
| gc_pause_max_ms | 66.0 |
| gc_pause_total_ms | 1341.4 |
| gc_count | 152.9868 |
| alloc_rate_mb_s | 62.6 |
| threads_max | 195.0 |
| process_cpu_avg | 0.5929 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.2 | 3.01 | 269 |
| payflow-kafka-1 | 56.2 | 162.04 | 992 |
| payflow-keycloak-1 | 0.2 | 0.32 | 1146 |
| payflow-mongo-1 | 7.1 | 28.93 | 239 |
| payflow-payflow-1 | 131.9 | 205.62 | 597 |
| payflow-postgres-1 | 41.7 | 78.8 | 566 |
| payflow-postgres-exporter-1 | 1.3 | 7.15 | 17 |
| payflow-prometheus-1 | 0.5 | 1.6 | 99 |
| payflow-settlement-rail-1 | 1.1 | 7.44 | 60 |
| payflow-tempo-1 | 0.5 | 2.43 | 137 |
