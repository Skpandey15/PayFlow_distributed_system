# WP-03 Failure Matrix

**How each failure was tested:**
- **LIVE:** a real fault injected into the compose lab under steady load (20 payments/s) with
  `performance/scripts/failure-campaign.sh`. Evidence is in `performance/results/failure-*/summary.txt` and
  `failure-campaign.log`.
- **IT:** an integration test with real containers.
- **PERF:** a load-test run.

Each LIVE run ends with a data-safety check: a reconciliation run (nine invariants) plus SQL checks for double
capture, double ledger journal, and SETTLED without a completed settlement.

**Scope actually executed:** 12 LIVE scenarios: F-01, F-03, F-06, F-14, F-21 in the first campaign, then F-02, F-04,
F-05, F-07, F-08, F-15, F-22 in the gap-closing campaign (3 min of 20 payments/s each, fault at 60 s). Every one
ended with 0 open mismatches, 0 double capture, 0 double journal and 0 SETTLED without a completed settlement.

Note: the first F-06 attempt injected its fault into host port 8090, which on this lab host is a Jenkins instance, not the rail simulator. Jenkins rejected the request (403, nothing changed there), and the run measured a healthy rail. It was invalidated (`performance/invalidated/`) and re-run with in-network injection. The others rely on IT or PERF
evidence, or on design only where stated. F-11 is the only scripted scenario still not run live.

Note on `alerts_fired`: the query covers the scenario window, so an alert still firing from the previous scenario
(for example the DLT alerts after F-07) also shows in the next one's summary.

## Critical finding produced by this campaign

> **F-03 exposed a real defect: the Kafka ACLs introduced for K1 silently disabled every retry and DLT consumer.**
>
> Spring's non-blocking retry derives consumer groups (`ledger-service-ledger-service-retry-0`, …), but the grants
> were for literal group names only (130 × `GroupAuthorizationException`).
>
> - During the PostgreSQL pause, one `FundsCaptured` went to the ledger's retry topic and was never processed.
> - **Reconciliation detected it:** CRITICAL `CAPTURE_POSTED_TO_LEDGER` + 2 × `LEDGER_MATCHES_BALANCE`, confirmed
>   across runs. `ReconciliationCriticalMismatch` fired.
> - Root cause traced through logs, the retry topic (offset 0, unconsumed) and the group list (no retry groups).
> - **Fix:** prefixed group grants (`bootstrap-security.sh`, `acl-matrix.sh`) plus a regression probe in
>   `verify-security.sh` (9/9 pass).
> - After an application restart the retry consumer processed the record idempotently, the journal was posted, and
>   reconciliation marked all three mismatches RESOLVED. **No financial data was edited by hand.**
>
> **Impact on earlier runs:** from the secured-Kafka stack onwards (the WP-03 performance runs), messages routed to
> retry topics were not processed:
> - Saga participants converged anyway, because saga recovery re-issues commands. No run ended with open sagas.
> - Ledger followers could miss postings: a handful of CONCURRENCY retries per run, 0 DLT.
> - The latency and throughput numbers are unaffected. The ledger totals of those runs are not evidence of anything.
>
> Integration tests (PLAINTEXT Kafka) could not catch it: security configuration must be tested in the secured
> environment.

## Matrix

| # | Failure | Evidence | User impact | Data safety | Detection | Recovery | SLO impact |
|---|---|---|---|---|---|---|---|
| F-01 | Kafka unavailable (stopped 90 s) | **LIVE**: 2,578 × 201, then **986 × 503** once the oldest unpublished event exceeded 60 s; recovery held; all 2,613 payments COMPLETED after restart; max outbox age 95 s | accepted payments delayed (completion p99 158 s); new payments refused with Retry-After after 60 s | 0 mismatches, 0 double capture or journal | OutboxPublicationLagging/Stalled; burn-rate alerts **fired**; PaymentCompletionSlow fired | automatic: relay drains in order; 196 s to quiet | A1 burned by the 503 shedding (deliberate); C1 missed during the outage |
| F-02 | Kafka slow / degraded (broker at 0.15 CPU for 90 s) | **LIVE** `failure-F02-kafka-degraded-20260929-141207`: 3,630 × 201, **0 × 503**; max outbox age 2 s; 321 commands re-issued by recovery; all 3,637 COMPLETED; no alert fired | completion p99 113 s during the fault; acceptance unaffected | 0 mismatches, 0 double capture or journal | outbox age, send latency, completion p99 (the age threshold was never reached, so admission did not shed) | automatic; 169 s to quiet | C1 missed during the fault |
| F-03 | PostgreSQL unavailable (paused 45 s) | **LIVE**: 844 × 503 (fast, classified; no 500), 2,748 × 201; all payments completed; **found the ACL defect above**; plus IT `SagaFailureIT.postgresOutageMidWorkflowConverges` | 503 during the pause; readiness DOWN, liveness UP | transactions: no partial writes; **1 lost ledger posting from the ACL defect, detected and healed** | burn-rate alerts; ReconciliationCriticalMismatch | automatic after the DB returns (after the ACL fix) | A1 burned for the outage duration |
| F-04 | PostgreSQL slow → pool exhaustion (0.1 CPU for 60 s) | **LIVE** `failure-F04-postgres-slow-pool-exhaustion-20260929-141731`: 3,070 × 201 and **513 × 503** (fast shedding, no hangs); max outbox age 14 s; all 3,074 COMPLETED. Also **PERF** `stress-wp03` → `stress-final` | some new payments refused with Retry-After; completion p99 67 s | 0 mismatches, 0 double capture or journal | DatabasePoolSaturated, PaymentAcceptanceBudgetSlowBurn **fired** | automatic; 195 s to quiet | A1 burned by the 503s (deliberate) |
| F-05 | MongoDB unavailable (stopped 150 s) | **LIVE** `failure-F05-mongodb-unavailable-20260929-142251`: fail closed; 174 fraud commands dead-lettered, 722 re-issued by recovery; all 3,630 COMPLETED after restart; 0 × 503. Also **IT** `SagaFailureIT.fraudStoreOutageIsRecoveredByTheSagaScanner` | risk step delayed (completion p99 179 s); never approved unscored | no authorization without a decision | DeadLettersAppearing, DeadLetterSpike, PaymentCompletionSlow **fired** | automatic (recovery re-issue); 166 s to quiet | C1 missed during the outage |
| F-06 | Settlement rail unavailable (every call 503 for 90 s) | **LIVE** (re-run; the first attempt was invalid, see note): acceptance unaffected (3,601 × 201, 0 × 5xx); **breaker opened at once: 52 real calls, 6,799 CIRCUIT_OPEN fast failures**; 1,444 commands dead-lettered after the 13 s retry window, then saga recovery re-issued 1,447 after the 2-min settlement timeout; **all 3,612 payments completed**; plus IT `SettlementRailResilienceTest` | completion delayed (p99 360 s); no failures visible to customers | idempotency key: 0 double settlement; 0 mismatches; no manual review needed | SettlementCircuitOpen, DeadLettersAppearing and **DeadLetterSpike fired**, PaymentCompletionSlow fired | automatic: breaker half-open probes plus saga recovery; 447 s to quiet | C1 missed during the outage; A1 unaffected |
| F-07 | Settlement rail slow (1.7 s ± 0.2 s for 90 s) | **LIVE** `failure-F07-settlement-slow-bulkhead-20260929-142914`: the slow-call rate opened the card and bank circuits; 6,500 calls failed fast as CIRCUIT_OPEN (NOT_SENT); **1,373 settlement commands dead-lettered**, 1,344 re-issued by recovery; all 3,629 COMPLETED; 0 × 503. Also **IT** `slowRailOpensTheCircuitOnSlowCallsBeforeCallsTimeOut` | settlements delayed (completion p99 358 s); acceptance unaffected | 0 mismatches; NOT_SENT is never charged twice | slow-call rate, DeadLetterSpike, PaymentCompletionSlow **fired** | automatic; **446 s to quiet** (slowest scenario) | C1 missed; see review R-2 (circuit-open commands go through retry→DLT→recovery) |
| F-08 | Timeout / unknown outcome (30 % TIMEOUT_AFTER_ACCEPT for 90 s) | **LIVE** `failure-F08-settlement-timeout-unknown-outcome-20260929-143916`: 131 rail timeouts; every one resolved to the rail's recorded ACCEPTED outcome (same-key retry/inquiry); all 3,670 COMPLETED; 0 DLT; 0 re-issues. Also **IT** `timeoutIsAnUnknownOutcomeNeverADeclineAndIsNotRetried` | delay (completion p99 82 s) | **never declined on timeout; 0 double settlement, 0 SETTLED without a completed settlement** | rail outcome `TIMEOUT` | same-key retry / inquiry; 165 s to quiet | C1 missed during the fault |
| F-09 | Circuit breaker opens | **LIVE** F-06 (52 real calls, then fast failures) + **IT** | fast failure instead of hanging | – | circuit-state panel, alert | – | – |
| F-10 | Half-open recovery | **IT** `circuitOpens…RecoversThroughHalfOpen` | – | – | state-change log | automatic after 15 s | – |
| F-11 | Retry storm attempt | **LIVE** F-06: 3,600 payments during a full outage produced 52 calls to the rail; the breaker absorbed 6,799 attempts, and recovery re-issue is paced by the 2-min step timeout | – | – | retry and rail call counters | – | – |
| F-12 | Bulkhead saturation | **IT** `bulkheadCapsConcurrentCalls…` (in flight ≤ limit; BULKHEAD_FULL = NOT_SENT; breaker unaffected) | – | – | bulkhead permits panel | – | – |
| F-13 | Rate-limit exhaustion | **IT** `TrafficControlIT` (429 + Retry-After per subject; other subjects unaffected); PERF hot-account-final2 (50,363 shed) | offending caller throttled | – | traffic rejected panel | – | 429 not counted against A1 |
| F-14 | Consumer crash (SIGKILL) | **LIVE**: 20 s kill under load; all 2,905 payments completed; 1 recovery re-issue; 0 double effects; plus IT crash-before/after-commit (WP-02) | acceptance down for 20 s (a single instance) | inbox/idempotency: 0 duplicates | target down, burn rate | restart; offsets committed after processing | A1 for the downtime |
| F-15 | Consumer rebalance (second instance joins for 90 s, then leaves) | **LIVE** `failure-F15-rebalance-extra-instance-20260929-144438`: two cooperative-sticky rebalances under load; 0 DLT, 0 re-issues; all 3,629 COMPLETED; completion p99 9.7 s. Also **PERF** two-instance peak | none (completion p99 9.7 s vs 2.3 s steady) | idempotent consumers: 0 double capture or journal | lag | cooperative-sticky; 119 s to quiet | – |
| F-16 | Outbox backlog grows | **LIVE** F-01; **IT** TrafficControlIT (Kafka paused → 503) | shed after 60 s | – | outbox age alerts | drain | A1 (shedding) |
| F-17 | Saga stuck | **IT** SagaFailureIT recovery tests; recovery hold unit test | delay | re-issue is idempotent | SagaStuck | automatic | C1 |
| F-18 | Manual-review workflow | **IT** `ManualReviewIT` (both paths, audit, idempotency, scope) | funds held until resolved | no forced outcome; the rail is ground truth | ManualReviewWaiting/Aging | operator | C2 |
| F-19 | Reconciliation mismatch | **IT** `ReconciliationIT`; **LIVE** real drift found and healed (above) | none | detect-only; never repaired by SQL | ReconciliationCriticalMismatch **fired live** | fix the cause, replay | R1 |
| F-20 | Hot-account contention | **PERF** hot-account-final2: `Lock:transactionid` waits in every sample; 0 deadlocks; 62/s accepted, rest shed; acceptance p99 208 ms | the hot payer is throttled | row lock preserves the balance invariant | pool and lock panels | – | A2 held |
| F-21 | Graceful shutdown (SIGTERM, 45 s grace) | **LIVE**: all 3,078 accepted payments completed; **0 × 5xx during shutdown** (0 × 503 recorded; clients got connection errors while it was down); 0 double effects | acceptance unavailable while restarting (a single instance) | in-flight records redelivered and deduplicated | target down | restart | A1 for the restart window |
| F-22 | Application restart (`docker restart -t 45`) | **LIVE** `failure-F22-restart-20260929-144958`: graceful stop, restart ~60 s; requests during the gap failed at the client (no 5xx served); max outbox age 5 s; 19 re-issues; all 3,310 COMPLETED | about a minute of unavailability for a single instance (production runs ≥ 2 replicas with a PDB) | 0 mismatches, 0 double capture or journal | PaymentCompletionSlow fired | automatic; 264 s to quiet | A1 during the gap on a single instance |
| F-23 | Sustained load | **PERF** 10-min normal/peak runs; **45-min soak NOT run** (review O-1) | – | – | – | – | – |
| F-24 | Burst traffic | **PERF** burst-final: acceptance p99 268 ms; completion p99 136 s; everything drained in 30 s | slower completion during spikes | – | PaymentCompletionSlow | automatic | C1 during spikes |
