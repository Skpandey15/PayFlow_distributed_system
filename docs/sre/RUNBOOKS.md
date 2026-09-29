# Runbooks (WP-03)

Every runbook has the same shape: **symptoms → metrics → logs → traces → likely causes → safe actions → unsafe
actions → escalation.**

Global rules:
- Never edit financial tables by hand.
- Never replay a DLT or resolve a review without reading the evidence first.
- Quote correlation ids in tickets.

---

## High Kafka lag {#high-kafka-lag}

**Symptoms:** `KafkaConsumerLagGrowing`; completion latency rising; saga open counts rising in one step.

**Metrics:**
- Kafka dashboard → lag per group and topic (broker-side, trustworthy even if the consumer died).
- Consumer p99 by consumer; failures by category.
- DB pool (consumers write to PostgreSQL).

**Logs:**
- `payflow.events.consumer` WARN lines for the group, with failureCategory and errorCode.
- Rebalance messages (`partitions revoked/assigned`).

**Traces:** Tempo, filter on span name `*-receive` for the topic; look for long jdbc spans or lock waits.

**Likely causes:**

| Cause | What it looks like |
|---|---|
| Consumer crashed or stuck | lag grows linearly, consumed/s = 0 |
| Processing slowed by the DB (locks, pool) | consumer p99 up, DB acquire wait up |
| Retry loop on a transient dependency | failures TRANSIENT_INFRASTRUCTURE |
| Partition count caps parallelism | lag high on some partitions, idle consumers |
| Hot partition (one key dominating) | lag concentrated in one partition |

**Safe actions:**
- Restart a crashed instance (offsets are committed only after processing: no loss, duplicates are deduplicated).
- Scale instances **up to the partition count** (6); beyond that, extra consumers idle.
- Fix the dependency that is failing.

**Unsafe actions:**
- Resetting consumer offsets to latest: skips payments.
- Deleting retry topics.
- Raising `max.poll.records` blindly: longer transactions and rebalances.

**Escalation:** Platform on-call if the broker is unhealthy; the owning context team if one group lags.

---

## Outbox backlog {#outbox-backlog}

**Symptoms:**
- `OutboxPublicationLagging` (15 s) or `OutboxPublicationStalled` (60 s).
- At 60 s **admission control returns 503 to new payments**.
- `SagaRecoveryHeld` fires as a consequence.

**Metrics:** Outbox dashboard.

| What you see | What it means |
|---|---|
| Published/s ≈ 0 and publish failures/s > 0 | Broker unreachable or authentication failing |
| Published/s flat at a ceiling while production is higher | Relay capacity |
| Ack wait p99 high | Slow broker |

**Logs:** `outbox publication failed; will retry in order` with errorCode (e.g. `TimeoutException`,
`SaslAuthenticationException`); `outbox relay poll failed` (DB).

**Likely causes:**

| Cause | What to check |
|---|---|
| Kafka down | Kafka dashboard; `docker ps` or kubectl |
| SCRAM credentials changed or ACL removed | logs show auth/authorization errors |
| Broker slow (disk, ISR shrink) | broker metrics |
| Relay throughput ceiling at high production rate | BOTTLENECK-ANALYSIS |
| Advisory lock held by a hung replica | `select * from pg_locks where locktype='advisory'` |

**Safe actions:**
- Restore the broker or credentials. The relay resumes in id order automatically, and events are not lost.
- Restart a hung replica (its transaction-scoped lock is released).
- Leave admission control on; it protects the SLO for accepted payments.

**Unsafe actions:**
- Deleting or "marking published" outbox rows: sagas stall forever.
- Disabling admission control during an outage.
- Running two relays against one outbox without the lock: breaks order.

**Escalation:** Platform (Kafka). If the relay ceiling is the cause, performance owner (ADR-019 options C/E).

---

## DLT spike {#dlt-spike}

**Symptoms:** `DeadLettersAppearing` / `DeadLetterSpike`.

**Metrics:** Kafka dashboard → dead-lettered by topic and category; failures by category before the spike.

**Logs:** DLT observer ERROR line per record (sanitised headers: category, errorCode, original topic, partition,
offset). No payloads and no stack traces in the DLT by design.

**Traces:** find the original delivery by traceId in the header.

**Likely causes:**

| Category | Cause |
|---|---|
| TRANSIENT_INFRASTRUCTURE | a dependency down longer than the retry window (1 + 3 + 9 s) |
| CONTRACT_VIOLATION / DESERIALIZATION | a producer bug or incompatible deploy |
| BUSINESS_RULE | e.g. capture of released funds (race) |
| UNTRUSTED_SOURCE | forged or misrouted message: security incident |

**Safe actions:**
- Fix the cause first.
- For transient causes, saga recovery usually re-issues the command anyway (check whether the payments already
  completed).
- Replay individual records with `POST /api/v1/ops/dead-letters/replay` (scope `ops:dlq-replay`, rate limited).
  Replay goes to the group's own retry-0 topic, and the inbox makes it at most once.

**Unsafe actions:**
- Bulk replay before the cause is fixed: straight back to the DLT, noise.
- Replaying UNTRUSTED_SOURCE records.
- Editing payloads.

**Escalation:** the owning team named in the topic suffix; security on UNTRUSTED_SOURCE.

---

## Saga stuck {#saga-stuck}

**Symptoms:** `SagaStuck` / `SettlementStepStuck`; customers see PROCESSING for a long time.

**Metrics:**
- Saga dashboard → oldest age per step, open per step, recovery actions and holds.
- Lag for the step's participant.

**Logs:** `payflow.saga.recovery`, which shows either "overdue saga recovered" (re-issue) or "saga recovery held".

**Trace:** by correlationId, find the last hop that ran.

**Likely causes:**

| Cause | What it looks like |
|---|---|
| Participant down (consumer lag) | re-issues pile up in its topic |
| Recovery held because the outbox is behind | fix the outbox first |
| Recovery job failing (DB) | `saga recovery run failed` |
| Rail circuit open | settlement step |

**Safe actions:**
- Fix the participant; recovery re-issues automatically (bounded, 3 attempts).
- Inspect one saga: `GET /api/v1/payments/{id}/saga` (payments:admin).

**Unsafe actions:**
- Updating `payment_saga.step` by SQL.
- Emitting replies by hand: the saga would advance on a lie.

**Escalation:** owning team of the participant.

---

## Manual review {#manual-review}

**Symptoms:** `ManualReviewWaiting` (ticket) / `ManualReviewAging` (page at 4 h). Customer funds are reserved.

**Procedure** (scope `ops:manual-review`):
1. List cases: `GET /api/v1/ops/manual-reviews` (oldest first).
2. Open a case: `GET /api/v1/ops/manual-reviews/{paymentId}`. Read:
   - `reviewCase.escalatedFrom`
   - `settlement.submissionAttempts`, `lastAttemptOutcome` (NOT_SENT = never reached the rail; UNKNOWN = may
     have), `lastErrorCode`
   - `settlement.railState`, from a **live inquiry**:

     | railState | Meaning | Decision |
     |---|---|---|
     | ACCEPTED | money moved | `RESUME` (completes via capture) |
     | NOT_FOUND | the rail never received it | `CONFIRM_NOT_SETTLED` (voids the key at the rail, then releases funds via the normal decline path) or `RESUME` (tries again) |
     | DECLINED | the rail declined it | `RESUME` (the decline flows through and compensates) |
     | UNREACHABLE | no evidence | **Do not decide.** Wait for the rail. |

   - For AWAITING_CAPTURE and COMPENSATING escalations, only `RESUME` is offered (re-issue capture or release;
     both idempotent).
3. Decide: `POST /api/v1/ops/manual-reviews/{paymentId}/decisions` with an `Idempotency-Key` header and body
   `{"decision": "...", "reason": "...", "ticketReference": "OPS-123"}`.
4. Confirm the payment reaches its terminal state and that no reconciliation finding appears for it.

**Unsafe actions (and why they do not exist):** "mark settled" / "mark failed" / releasing funds directly. The only
path to an outcome is through the participants and the rail's own records.

**Escalation:**
- 409 `RAIL_IDEMPOTENCY_WINDOW_EXPIRED` → finance and the rail provider (manual settlement investigation).
- 409 `RAIL_REPORTS_SETTLED` on CONFIRM → re-read the evidence, then RESUME.

---

## Database pool saturation {#database-pool-saturation}

**Symptoms:** `DatabasePoolSaturated`; 503s with `DependencyUnavailable` (pool timeout 3 s); p99 rising.

**Metrics:**
- DB dashboard: active vs max, pending, acquire wait, **usage (connection hold) time**.
- Locks and deadlocks; backends by state ("idle in transaction" is a red flag).

**Logs:** `dependency unavailable` WARN with the pool timeout; `SQLTransientConnectionException`.

**Traces:** jdbc CONNECTION spans show acquisition wait inside a request.

**Likely causes:**

| Cause | Where to look |
|---|---|
| Long transactions | usage time up |
| Lock contention (hot account; FOR UPDATE queue) | `pg_locks` waiting |
| Slow query after data growth | `pg_stat_statements`: mean and total time |
| Connection leak | active stays at max with low throughput |
| Pool too small for the instance's concurrency (rare; see CAPACITY-PLAN) | |

**Safe actions:**
- Identify the top statements (`pg_stat_statements` ordered by total_exec_time).
- Kill a clearly runaway query **only** if it is a read; the transaction rolls back.
- Scale **only** if `replicas × pool ≤ PostgreSQL budget` (CAPACITY-PLAN).

**Unsafe actions:**
- Raising pool size or replicas blindly: more connections compete for the same CPU, locks and I/O and usually
  make p99 worse.
- Disabling the transaction timeout.

**Escalation:** DBA / platform.

---

## Circuit breaker open {#circuit-breaker-open}

**Symptoms:** `SettlementCircuitOpen` (ticket) → `SettlementCircuitOpenProlonged` (page).

**Metrics:** Resilience dashboard: state per rail, rail call outcomes (TIMEOUT? UNAVAILABLE? THROTTLED?), failure
and slow-call rate, bulkhead permits.

**Logs:**
- `circuit breaker state changed` (payflow.resilience).
- Consumer WARN lines with `dependency=settlement-rail:<RAIL>`, `circuitBreakerState`, `deliveryOutcome`.

**Likely causes:** provider outage, provider throttling, our network egress, provider slowness (slow-call rate).

**Safe actions:**
- Check the provider status and contact it.
- Let the breaker probe; nothing is lost. Settlements retry, then DLT, then recovery re-issues, and after exhaustion
  they reach MANUAL_REVIEW with funds held.
- After recovery, watch the manual-review queue.

**Unsafe actions:**
- Forcing the breaker closed.
- Raising the timeout far above 2 s: UNKNOWN outcomes get slower, not fewer.
- Treating open-circuit failures as declines.

**Escalation:** provider relationship owner; payments on-call.

---

## Reconciliation mismatch {#reconciliation-mismatch}

**Symptoms:** `ReconciliationCriticalMismatch` (page) / `ReconciliationHighMismatch` / `ReconciliationNotRunning`.

**Data:**
- `GET /api/v1/ops/reconciliation/mismatches` (scope `ops:reconciliation`): check, subject, expected vs actual,
  times seen, first seen.
- Dashboard: misstated amount per currency.

**Likely causes by check:**

| Check | Typical cause |
|---|---|
| LEDGER_MATCHES_BALANCE, CAPTURE_POSTED_TO_LEDGER | a dead-lettered `FundsCaptured` / `FundsDeposited` not replayed (see DLT); manual SQL |
| RESERVED_MATCHES_RESERVATIONS | a code path updating the balance outside FundsService (bug) |
| COMPLETED_SETTLEMENT_HAS_SETTLED_PAYMENT | capture never completed (check the saga) |
| NO_HOLD_ON_FINISHED_PAYMENT | release command dead-lettered |
| LEDGER_ZERO_SUM | ledger integrity broken (should be impossible: balanced-journal trigger). Treat as a severe incident. |

**Safe actions:**
- Find the causing message (DLT) or change (audit), then fix the **cause**. For a missing posting, replay the DLT
  record: the ledger posts idempotently by journal reference.
- Re-run reconciliation (`POST .../runs`) to confirm the finding resolves.

**Unsafe actions:**
- Updating balances or ledger rows to "make them match". Reconciliation never repairs, and neither do humans with
  SQL. Corrections are compensating journals approved by finance.

**Escalation:** finance + payments engineering (CRITICAL immediately).

---

## High p99 latency {#high-p99-latency}

**Symptoms:** `PaymentAcceptanceLatencyBudgetBurn`, `PaymentCompletionSlow`, `JvmGcOverhead`.

**First, decide which latency:** acceptance (HTTP) or completion (saga). They have different causes.

**Acceptance p99:**

| Look at | Suspect |
|---|---|
| DB acquire wait / usage | pool or locks |
| GC max pause / pause time | JVM (was SerialGC in the baseline; see TUNING-RESULTS) |
| CPU | saturation |
| Admission (503 is fast, not slow) | |
| Hot account | row-lock queue on account_balance, only if payments hit one payer |

**Completion p99:** "why are payments taking longer" panel.

| Rising alongside step p95 | Where the problem is |
|---|---|
| outbox age | publication |
| consumer lag | consumption |
| alone, in AWAITING_SETTLEMENT | rail (see the Resilience dashboard) |

**Traces:** pick slow traces (exemplars); compare span durations: jdbc vs http.client vs gaps (queueing).

**Safe actions:**
- Shed load (admission control does this automatically for outbox age).
- Scale the stateless path (only if not DB-bound).
- Roll back a recent deploy.

**Unsafe actions:**
- Raising timeouts to hide it.
- Raising pool sizes without evidence.

---

## Error-budget burn {#error-budget-burn}

**Symptoms:** A1 burn alerts; `PayFlowTargetDown`.

**Steps:**
1. Which status codes? 503 with code `PAYMENTS_TEMPORARILY_UNAVAILABLE` = admission shedding (go to *Outbox
   backlog*). 503 `DependencyUnavailable` = DB or pool. 500 = bug (logs: `unhandled exception`, one line per
   request with correlationId).
2. Recent deploy? Roll back first, investigate second.
3. `PayFlowTargetDown`: is the instance down (kubectl/docker), or is the IdP down (the scrape token is refused)?
   The IdP case is a monitoring outage, not a customer outage, but JWT validation of **new** customer tokens also
   depends on JWKS availability.

**Policy consequences** (ERROR-BUDGET.md): at ≥ 75 % consumption freeze risky releases; at 100 % feature freeze.
