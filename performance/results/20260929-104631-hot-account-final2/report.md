# Load-test report: 20260929-104631-hot-account-final2

Window: 2026-09-29T05:16:34.669000+00:00 to 2026-09-29T05:26:04.734000+00:00 (+125s drain). Rate scale 1.

## Client (k6, open model)

| Metric | Value |
|---|---|
| iterations | 85916 |
| iterations_per_s | 150.28574408668575 |
| dropped_iterations | 33 |
| payments_accepted | 35452 |
| payments_throttled | 50363 |
| payments_rejected_at_api | 0 |
| checks_pass_rate | 0.513668800757071 |
| post_p50_ms | 2.653047 |
| post_p95_ms | 113.63221680000001 |
| post_p99_ms | 238.08445384000004 |
| post_max_ms | 923.076764 |

## Server (Prometheus)

| Metric | Value |
|---|---|
| accepted_per_s | 62.4584 |
| api_5xx_ratio | 0.5899 |
| api_post_p50_ms | 1.0 |
| api_post_p95_ms | 96.2 |
| api_post_p99_ms | 207.8 |
| api_get_p99_ms | 191.3 |
| saga_completed | 35708.8986 |
| saga_completion_by_outcome | {"outcome=COMPLETED": 35708.8986} |
| saga_p50_ms | 132116.7 |
| saga_p95_ms | 223917.1 |
| saga_p99_ms | 237940.9 |
| saga_step_p99_ms | {"step=AWAITING_CAPTURE": 118376.0, "step=AWAITING_FUNDS": 111475.0, "step=AWAITING_RISK": 88120.7, "step=AWAITING_SETTLEMENT": 33334.3} |
| drain_seconds_after_load | 125 |
| saga_open_at_load_end | {"step=AWAITING_CAPTURE": 1321.0, "step=AWAITING_FUNDS": 2276.0, "step=AWAITING_RISK": 4737.0, "step=AWAITING_SETTLEMENT": 159.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_after_drain | {"step=AWAITING_CAPTURE": 0.0, "step=AWAITING_FUNDS": 0.0, "step=AWAITING_RISK": 0.0, "step=AWAITING_SETTLEMENT": 0.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| saga_open_max | {"step=AWAITING_CAPTURE": 7681.0, "step=AWAITING_FUNDS": 6581.0, "step=AWAITING_RISK": 6894.0, "step=AWAITING_SETTLEMENT": 1809.0, "step=COMPENSATING": 0.0, "step=MANUAL_REVIEW": 0.0} |
| outbox_publish_delay_p50_ms | 149.3 |
| outbox_publish_delay_p99_ms | 491.4 |
| outbox_publish_delay_p99_by_outbox_ms | {"outbox=account.outbox_event": 424.8, "outbox=payment.outbox_event": 499.8, "outbox=settlement.outbox_event": 507.6} |
| outbox_send_p99_ms | 72.1 |
| outbox_published_per_s | 589.5221 |
| outbox_backlog_max | {"outbox=account.outbox_event": 4.0, "outbox=payment.outbox_event": 169.0, "outbox=settlement.outbox_event": 3.0} |
| outbox_oldest_age_max_s | {"outbox=account.outbox_event": 0.0, "outbox=payment.outbox_event": 0.0, "outbox=settlement.outbox_event": 0.0} |
| consumer_lag_max_top5 | {"group=account-service,topic=funds.commands": 8984.0, "group=fraud-service,topic=fraud.commands": 6697.0, "group=payment-service,topic=funds.events": 3354.0, "group=payment-service,topic=fraud.events": 3270.0, "group=payment-service,topic=settlement.events": 1594.0} |
| consumer_p99_ms | {"consumer=payment-service": 65.3, "consumer=account-service": 111.0, "consumer=fraud-service": 103.5, "consumer=settlement-service": 101.4, "consumer=ledger-service": 50.7} |
| events_consumed_per_s | 520.76 |
| events_failed | {"category=CONCURRENCY": 10.1743} |
| events_dead_lettered | 0 |
| hikari_active_max | 20.0 |
| hikari_pending_max | 44.0 |
| hikari_acquire_max_ms | 828.5 |
| hikari_acquire_avg_ms | 1.1 |
| hikari_usage_avg_ms | 10.7 |
| hikari_timeouts | 0 |
| pg_commits_per_s | 1518.0655 |
| pg_rollbacks | 11.0321 |
| pg_deadlocks | 0 |
| pg_backends_max | 23.0 |
| mongo_cmd_max_ms | 180.1 |
| mongo_cmd_avg_ms | 4.4 |
| jvm_heap_used_max_mb | 249 |
| jvm_heap_after_gc_max_mb | 101 |
| jvm_heap_committed_max_mb | 264 |
| gc_pause_max_ms | 63.0 |
| gc_pause_total_ms | 4616.8 |
| gc_count | 961.0808 |
| alloc_rate_mb_s | 203.1 |
| threads_max | 75.0 |
| process_cpu_avg | 0.8569 |
| process_cpu_max | 1.0 |

## Containers (docker stats; CPU % of one core)

| Container | CPU avg % | CPU max % | Mem max MiB |
|---|---|---|---|
| payflow-grafana-1 | 1.1 | 6.29 | 323 |
| payflow-kafka-1 | 66.7 | 204.57 | 679 |
| payflow-keycloak-1 | 2.9 | 59.75 | 764 |
| payflow-mongo-1 | 38.2 | 103.01 | 377 |
| payflow-payflow-1 | 174.4 | 208.48 | 626 |
| payflow-postgres-1 | 86.0 | 153.32 | 595 |
| payflow-postgres-exporter-1 | 0.4 | 2.25 | 10 |
| payflow-prometheus-1 | 0.6 | 2.34 | 63 |
| payflow-settlement-rail-1 | 2.6 | 6.64 | 38 |
| payflow-tempo-1 | 2.4 | 12.7 | 130 |
