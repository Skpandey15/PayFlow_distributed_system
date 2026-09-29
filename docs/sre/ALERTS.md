# Alerts (WP-03)

Source of truth: `deploy/observability/prometheus/rules/payflow-alerts.yml` (validated with `promtool check rules`).

Rules of the alert set:
- Alert on **symptoms users or money feel**, plus a few **leading indicators** with a clear action.
- Never alert on a single transient failure: durations, burn-rate windows and "confirmed" (two-run) filters
  prevent noise.
- `page` wakes someone; `ticket` is handled in working hours.
- Every alert has a runbook.

| Alert | Signal | Threshold | For | Severity | Likely causes | First investigation step | Runbook |
|---|---|---|---|---|---|---|---|
| PaymentAcceptanceBudgetFastBurn | A1 5xx ratio 1 h and 5 m | > 14.4 × budget | (windows) | page | DB down or pool exhausted, admission shedding, bad deploy | Overview → 5xx by status; DB pool panel; admission shedding panel | [error-budget-burn](RUNBOOKS.md#error-budget-burn) |
| PaymentAcceptanceBudgetSlowBurn | A1 6 h and 30 m | > 6 × | (windows) | page | recurring pool timeouts, partial DB degradation | same | [error-budget-burn](RUNBOOKS.md#error-budget-burn) |
| PaymentAcceptanceLatencyBudgetBurn | A2 slow ratio 1 h and 5 m | > 6 % slow (> 300 ms) | (windows) | page | lock contention (hot account), pool wait, GC | Latency → p99; DB acquire wait; JVM GC | [high-p99-latency](RUNBOOKS.md#high-p99-latency) |
| PaymentCompletionSlow | C1 over 30 m | > 5 % over 30 s | 10 m | page | outbox backlog, consumer lag, slow rail | Overview "why are payments taking longer" | [high-p99-latency](RUNBOOKS.md#high-p99-latency) |
| OutboxPublicationLagging | max oldest unpublished age | > 15 s | 2 m | ticket | broker slow, relay near capacity | Outbox dashboard: published/s vs production | [outbox-backlog](RUNBOOKS.md#outbox-backlog) |
| OutboxPublicationStalled | same | > 60 s | 1 m | page | Kafka down or authentication failing; relay lock held by a hung replica | relay failures/s; broker health; logs `outbox publication failed` | [outbox-backlog](RUNBOOKS.md#outbox-backlog) |
| KafkaConsumerLagGrowing | broker-side lag > 1000 and rising | | 10 m | page | consumer crashed, poison-free slowness, DB slow | Kafka dashboard; consumer p99; failures by category | [high-kafka-lag](RUNBOOKS.md#high-kafka-lag) |
| ConsumerLagMonitorStale | seconds since last lag read | > 120 | 5 m | ticket | broker unreachable from PayFlow, ACL change | PayFlow logs; broker health | [high-kafka-lag](RUNBOOKS.md#high-kafka-lag) |
| DeadLettersAppearing | DLT increase over 15 m | > 0 | none | ticket | contract bug, business conflict, retry exhaustion | Kafka dashboard DLT by topic and category | [dlt-spike](RUNBOOKS.md#dlt-spike) |
| DeadLetterSpike | same | > 20 | none | page | systemic failure (dependency outage longer than the retry window, bad deploy) | same, plus dependency panels | [dlt-spike](RUNBOOKS.md#dlt-spike) |
| SagaStuck | oldest in risk, funds, capture or compensating | > 300 s | 5 m | page | participant down, recovery not running, recovery held | Saga dashboard; recovery actions and holds | [saga-stuck](RUNBOOKS.md#saga-stuck) |
| SettlementStepStuck | oldest in AWAITING_SETTLEMENT | > 900 s | 5 m | page | rail down, circuit open | Resilience dashboard | [saga-stuck](RUNBOOKS.md#saga-stuck) |
| SagaRecoveryHeld | recovery held increments | > 0 | 10 m | ticket | outbox backlog (symptom) | Outbox dashboard | [outbox-backlog](RUNBOOKS.md#outbox-backlog) |
| ManualReviewWaiting | open MANUAL_REVIEW | > 0 | 15 m | ticket | unknown settlement or capture outcome | `GET /api/v1/ops/manual-reviews` | [manual-review](RUNBOOKS.md#manual-review) |
| ManualReviewAging | oldest review case | > 4 h | none | page | nobody working the queue; funds held | same | [manual-review](RUNBOOKS.md#manual-review) |
| ReconciliationCriticalMismatch | confirmed CRITICAL mismatches | > 0 | none (already 2 runs) | page | DLT'd funds event, manual SQL, bug | `GET /api/v1/ops/reconciliation/mismatches` | [reconciliation-mismatch](RUNBOOKS.md#reconciliation-mismatch) |
| ReconciliationHighMismatch | confirmed HIGH | > 0 | 30 m | ticket | ledger posting missing, hold not released | same | [reconciliation-mismatch](RUNBOOKS.md#reconciliation-mismatch) |
| ReconciliationNotRunning | OK runs in 30 m | = 0 | 10 m | ticket | DB overload, job failing | logs `reconciliation run failed` | [reconciliation-mismatch](RUNBOOKS.md#reconciliation-mismatch) |
| SettlementCircuitOpen | breaker state open | = 1 | 2 m | ticket | rail outage or degradation | Resilience dashboard; provider status page | [circuit-breaker-open](RUNBOOKS.md#circuit-breaker-open) |
| SettlementCircuitOpenProlonged | same | = 1 | 15 m | page | prolonged rail outage | same; escalate to the provider | [circuit-breaker-open](RUNBOOKS.md#circuit-breaker-open) |
| DatabasePoolSaturated | pending > 0 and active/max > 90 % | | 5 m | page | slow queries, lock waits, long transactions, pool too small | DB dashboard; pg_stat_activity | [database-pool-saturation](RUNBOOKS.md#database-pool-saturation) |
| JvmGcOverhead | GC pause time share | > 10 % | 10 m | ticket | heap too small, leak, SerialGC | JVM dashboard | [high-p99-latency](RUNBOOKS.md#high-p99-latency) |
| PayFlowTargetDown | `up{job="payflow"}` | = 0 | 1 m | page | instance down, scrape token rejected (IdP) | `docker ps`/kubectl; Prometheus targets page | [error-budget-burn](RUNBOOKS.md#error-budget-burn) |

**Deliberately not alerted:**

| Signal | Why |
|---|---|
| Single 5xx responses | burn rates cover them |
| One retry-topic delivery | normal operation |
| Circuit HALF_OPEN | transient by design |
| Rate-limit 429s | caller behaviour; visible on dashboards |
| CPU or heap by themselves | causes, not symptoms. GC overhead is the exception because it directly adds latency. |
