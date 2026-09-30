# WP-03 Architecture Technical Review

The review tries to find flaws. Evidence is in `performance/results` and `docs/performance`. Nothing is marked
proven without a run.

## Verdict

**No unresolved BLOCKER.** WP-03 is complete as a lab work package. Accepted MAJOR findings are listed with risk,
reason, mitigation and owner. PayFlow is **not** declared production-ready: tests passing and lab runs on one host
are not production evidence.

## Findings

### BLOCKER
None open. Two blockers were found **during** WP-03 and fixed; both are measured:

| Blocker | Fix |
|---|---|
| The relay could not sustain 1× load: completion p99 548 s | Pipelined relay (ADR-019) |
| Unbounded intake under overload: 38.6 s GC pause, 32 % 5xx | In-flight bulkhead + G1 |
| **Kafka ACLs (K1) silently disabled all retry/DLT consumers** (derived group names not granted). Found live by reconciliation in F-03 (a lost ledger posting) | Prefixed group grants, a regression probe in verify-security.sh (9/9), healed without data edits (WP-03-FAILURE-MATRIX) |

### MAJOR (accepted)

| ID | Finding | Risk | Reason accepted | Mitigation now | Owner / future |
|---|---|---|---|---|---|
| P-1 | One 2-vCPU instance misses the acceptance p99 SLO at peak (372 ms vs 300 ms, G1); SerialGC meets it (173 ms) but has an unbounded worst-case pause | tail latency at peak if only one pod runs | Two instances meet it (185 ms p99, completion 4 s, measured); production runs `minReplicas: 2` | PDB minAvailable 1, HPA on CPU | Platform: 3 vCPU per pod or keep ≥ 2 replicas |
| P-2 | ~~Completion p99 under bursts and stress is minutes~~ **Closed.** Admission is now a work-in-progress window: Little's law bounds completion time by bounding the payments in PayFlow's own pipeline (TUNING-RESULTS §11). Same host: burst completion p99 113 → 18.7 s, stress 215 → 25.0 s, drain 96 → 32 s, acceptance p99 inside the SLO in both | under sustained overload, accepted throughput drops (68 → 52/s): more payments get a fast 503 | refusing is better than accepting work that misses the SLO | `payflow_traffic_admission_work_in_progress`, credits, `payflow_traffic_rejected_total{policy="work-in-progress"}` | 400 was tried and rejected (burst acceptance p99 651 ms); scale the limit with replicas |
| P-3 | Hot account: one payer is serialised by its row lock (`Lock:transactionid` waits in every sample); rate limiting is per subject, so a merchant tier needs a higher limit | a merchant account caps at its lock throughput | correctness first (no weaker isolation) | per-subject limits; 0 deadlocks (deterministic lock order) | sub-accounts / batched reservations (BOTTLENECK-ANALYSIS §3) |
| O-1 | ~~Soak not executed~~ **Closed.** 45 min at 20/s (TUNING-RESULTS §8): acceptance p99 55.7 ms, completion p99 2.26 s, heap after GC flat at ≤ 94 MB, 0 DLT, 0 pool waits. It found the outbox growing at ~1.9 GB/h before the 1 h retention started purging; retention is now 15 min and the purge is proven (436k → 0 rows, 477 → 41 MB) | – | – | purge metrics, table-size monitoring via postgres-exporter | weekly soak job (CI level 3) |
| O-2 | ~~Failure campaign reduced~~ **Mostly closed.** 13 of 24 scenarios now run LIVE (F-01..F-08, F-11, F-14, F-15, F-21, F-22), all with clean data-safety checks; the rest are IT/PERF-backed; every scripted scenario has run | untested interactions for the IT-only rows | the rest need multi-node infrastructure | ITs with real containers | full `failure-campaign.sh` weekly |
| K1' | Kafka authenticated and authorised but **not encrypted**; the monolith uses one identity | payload sniffing on the network; a compromised app can write any PayFlow topic | TLS/mTLS certificate management out of scope | SCRAM, deny-by-default ACLs, per-service matrix applied and verified | Platform: SASL_SSL/mTLS; identities on extraction |
| R-1 | Reconciliation reads four schemas (documented exception to isolation) and scans the full ledger each run | cost grows with data | only way to get one consistent snapshot | read-only, advisory-locked, duration metric | incremental reconciliation before 10× |
| R-2 | ~~Circuit-open settlement commands go through retry → DLT → saga re-issue~~ **Closed.** They are now parked and resumed per rail at a bounded rate, and the resumer is their only driver. Same-host F-07 (TUNING-RESULTS §10): 1,526 → 0 DLT, time to quiet 376 → 178 s, completion p99 361 → 202 s, acceptance p99 242 → 183 ms. There's a kill switch (`PAYFLOW_SETTLEMENT_PARKING=false`) | none: saga recovery now defers parked settlements instead of re-issuing them (F-11: 1,097 → 15 re-issues) | – | `payflow_settlement_parked_total`, `payflow_settlement_resumed_total`, circuit-state panel | – |
| M4 (WP-01) | Schema isolation by convention | unchanged | unchanged | unchanged | extraction |

### MINOR
- The lab's host port 8090 collides with an existing Jenkins; the rail simulator's host mapping is unusable there, and fault injection now goes through the compose network.
- Retry-topic consumers cost ~50 % of a broker core while idle (≈ 30 long-polling containers).
- Tempo was OOM-killed at ~400 req/s with 10 % sampling; its memory was raised to 1.5 GiB. Production needs a
  collector with tail sampling.
- ~~Kubernetes manifests not deployed or validated~~ Closed: kubeconform 21/21 valid (`k8s-kubeconform.txt`), and the
  k3d-lab overlay deployed under the `restricted` Pod Security profile. A payment went end to end to SETTLED (`k8s-k3d-smoke.txt`).
  The deploy found a real bug: the base never set the Flyway migration role, so the pod ran DDL as `payflow_app` and
  crashed. It is now wired as in compose (`SPRING_FLYWAY_USER` plus the `db-migration-password` secret key).
- Per-subject rate limits are per instance (N replicas give N × the limit).
- Manual review has no four-eyes approval.
- The WP-03 build has higher CPU per payment than the baseline, because the pipeline now actually runs.
- The WP-03 performance runs were executed while retry consumers were disabled (the ACL blocker). Latency and
  throughput are unaffected; ledger totals from those runs are not used as evidence.

### OBSERVATIONS
- Bottlenecks masked each other: the relay hid pool/CPU contention, which hid hot-account locking. Each had to be
  re-discovered after the previous fix.
- Measurement integrity needed engineering:
  - A stale load generator contaminated 3 runs.
  - Data growth skewed runs by 2×.
  - A host sleep produced a 9 s "GC pause".
  - All of these are disclosed, and the affected runs were invalidated or re-run.
- The same A/B discipline rejected two plausible tunings: consumer concurrency 6, and SerialGC as the default.

## Challenge list (answered)

| Topic | Challenge | Answer / evidence |
|---|---|---|
| SLO realism | Are the targets achievable? | A1/A2 met at normal and burst; C1 met at normal and peak; peak A2 needs 2 instances (P-1) |
| Load model | Synthetic? | Yes, labelled; 12 msgs per payment derived and matched to measured rates |
| Outbox | Is the fix real? | 106 → 5,552 ev/s same-build A/B; publish delay p99 < 0.6 s in all final runs |
| Kafka scaling | Parallelism? | Partitions (6) bound it; replicas × concurrency ≤ partitions; 2-instance assignment verified (3 partitions each per topic set) |
| DB capacity | Pool size? | 20 is right; the pool saturated due to unbounded intake, not size; concurrency 6 proved more consumers hurt |
| JVM | Why G1? | Bounded worst case (47–71 ms max under stress vs 4.3–38.6 s Serial) |
| Retry amplification | Worst case? | ≤ 32 calls per settlement, collapsed by the breaker (RESILIENCE-ARCHITECTURE §2) |
| Backpressure | Where does work pile up? | Moved from outbox to lag; admission follows both (P-2 tuning open) |
| Graceful shutdown | Tested? | F-21 live (see failure matrix) |
| 10× | What breaks? | CAPACITY-PLAN §3: partitions, PostgreSQL primary, hot accounts, reconciliation scan |

## Final performance matrix (one instance unless noted; fresh database)

| Metric | WP-02 Baseline | WP-03 Result | Target/SLO | Status |
|---|---:|---:|---:|---|
| Throughput completed (sustainable) | ≈ 10–15 payments/s | ≈ 45–50 payments/s (1 inst.) | ≥ 50 at peak | met with 2 instances |
| API p50 (normal) | 7.4 ms | 11.7 ms | – | ok |
| API p95 (normal) | 18.6 ms | 28.8 ms | – | ok |
| API p99 (normal / peak) | 36 / 38 ms | 48 / 372 ms (185 ms, 2 inst.) | < 300 ms | met normal; peak needs 2 inst. |
| Saga p95 / p99 (normal) | 539 / 548 s | 2.0 / 2.1 s | 99 % < 30 s | met |
| Saga p99 (peak) | ≥ 600 s | 22 s (4 s, 2 inst.) | < 30 s | met |
| Outbox age max (normal / peak) | 145 / 395 s | 0 / 0 s | < 15 s | met |
| Kafka lag max, main topics (peak) | 1 (relay-starved) | 466 | bounded | ok |
| DB commits/s (peak) | 608 | 1,244 | – | – |
| DB connection utilisation (normal / peak) | 5 / 8 of 20 | 12 / 20 of 20 | no pending at normal | met normal |
| JVM heap max (stress) | 366 MB | 275 MB | < 1 GiB | ok |
| GC pause max (stress) | 4,291 ms | 47 ms | < 200 ms | met |
| Error rate (normal) | 0 | 0 | < 0.1 % | met |
| Error rate (stress) | 0.3 % 5xx + 105k unfinished | shed 503/429 by design | predictable | met (degradation) |
| DLT rate (all runs) | 0 | 0 | 0 | met |

## Final resilience matrix (implemented mechanisms only)

| Dependency | Timeout | Retry | Backoff / jitter | Circuit breaker | Bulkhead | Idempotency | Failure outcome |
|---|---|---|---|---|---|---|---|
| Settlement rail (HTTP) | connect 500 ms, response 2 s (inquiry 1 s) | 2 attempts, only connect-refused / 503 | 200 ms × 2ⁿ, ±50 % | per rail: 50 % failures or 80 % slow (> 1.5 s), 15 s open, 3 probes | semaphore 16 (payments) + 2 (operator), fail fast | key = paymentId (rail dedupes) | NOT_SENT / UNKNOWN → Kafka retry → DLT → recovery → MANUAL_REVIEW; never a decline |
| Kafka (producer via outbox) | max.block 5 s, request 5 s, delivery 15 s | idempotent producer; relay re-sends next poll | poll interval | – | single relay per outbox | eventId (inbox) | backlog in outbox; admission 503 at 60 s |
| Kafka (consumers) | – | retry topics 1 s / 3 s / 9 s | exponential | – | partitions × concurrency | inbox / natural keys | DLT + replay |
| PostgreSQL | pool acquire 3 s, tx 5 s | none (not retried in-request) | – | none (by design) | Hikari 20 + in-flight 48 | constraints, versions | 503 + Retry-After; readiness DOWN |
| MongoDB | selection 2 s, read 3 s | via Kafka retry | as above | – | – | assessment per payment (unique) | fail closed → risk timeout → REJECTED |
| Edge (inbound) | – | – | – | – | in-flight 48 | Idempotency-Key | 429 per subject; 503 admission |

## Final SLO matrix

| SLI | Measurement | SLO | Alert | Error budget | Evidence status |
|---|---|---|---|---|---|
| A1 acceptance availability | non-5xx POST / all | ≥ 99.9 % / 30 d | fast + slow burn | 43.2 min / 30 d | lab runs only (not a 30-day window) |
| A2 acceptance latency | 201 under 300 ms | ≥ 99 % | latency burn | 1 % slow | met normal/burst; peak with 2 inst. |
| C1 completion | COMPLETED within 30 s | ≥ 99 % | PaymentCompletionSlow | 1 % | met normal/peak; not under burst/stress |
| C2 no human intervention | escalations / terminal | ≥ 99.95 % | ManualReviewWaiting/Aging | 0.05 % | 0 escalations in load runs |
| P1 publication | published within 5 s | ≥ 99 % | OutboxPublicationLagging/Stalled | 1 % | met in all final runs |
| R1 financial integrity | confirmed CRITICAL mismatches | = 0 | ReconciliationCriticalMismatch | none | ReconciliationIT; failure-campaign checks |

## Final Principal Engineer answers

1. **Where does PayFlow bottleneck now?**
   - Per instance: CPU and the shared connection pool once the saga runs at full speed (≈ 45–50 completions/s).
   - Per account: the row lock.
   - Cluster-wide at 10×: partitions and the single PostgreSQL primary.
2. **How was it proven?**
   - USE/RED per run, isolated relay benchmark, same-build A/B, fresh DB per scenario, lock-wait sampling
     (`Lock:transactionid`).
3. **What changed?** Relay pipelining, recovery hold, bounded intake (bulkhead and admission), G1, 503
   classification, retention/purge, rail resilience, manual review, reconciliation, Kafka SCRAM/ACLs.
4. **Measured improvement:**
   - Relay × 52.
   - Normal completion p99 548 s → 2.1 s.
   - Peak: 20.5k unfinished → 0 (p99 22 s).
   - Stress: 105k unfinished → 0, acceptance p99 866 → 235 ms.
5. **Sustainable throughput:** ≈ 45–50 payments/s per 2-vCPU instance within the SLOs (peak p99 needs 2 instances).
6. **Saturation point:**
   - Acceptance ≈ 375/s CPU-bound when processing is starved.
   - With full processing, intake beyond ≈ 60–65/s is shed.
7. **At 10× traffic:**
   - Scale instances to partitions (plan 24 partitions).
   - Bigger PostgreSQL + PgBouncer.
   - Outbox partitioning.
   - Incremental reconciliation.
   - Hot accounts need sub-accounts (CAPACITY-PLAN §3).
8. **During a Kafka failure:**
   - Payments are accepted into the outbox, and recovery holds.
   - Admission returns 503 once the oldest event is 60 s old.
   - After recovery everything drains in order (F-01).
9. **During a PostgreSQL failure:**
   - Financial writes are impossible: fast 503 and readiness DOWN.
   - Liveness stays UP (no restart storm).
   - Transactions guarantee no partial state (F-03, SagaFailureIT).
10. **Settlement-provider degradation:**
    - The circuit breaker opens on failures or slow calls; calls fail fast as NOT_SENT; settlements retry, then DLT,
      then recovery or manual review.
    - Acceptance is unaffected (F-06, SettlementRailResilienceTest).
11. **Retry storms:** bounded, selective retries, jitter, per-rail breaker, delayed Kafka retries, recovery hold.
12. **Overload:** per-subject rate limit, in-flight bulkhead, admission on outbox age and consumer lag, all 503/429
    with Retry-After.
13. **p99 monitoring:** server histograms with exact SLO buckets, burn-rate alerts, latency dashboard.
14. **Critical SLIs:** acceptance availability and latency, completion within 30 s, publication within 5 s,
    financial integrity.
15. **SLOs chosen, and why:** SLI-SLO.md. They are derived from the architecture (one primary ⇒ 99.9 %) and the
    customer journey (30 s completion).
16. **Error budget:** 43.2 min per 30 days for A1, with a 50/75/100 % action policy (ERROR-BUDGET.md).
17. **Diagnosing a slow payment:** correlationId → logs → traceId → Tempo; step p95 vs outbox age vs lag panel.
18. **Diagnosing a stuck payment:** saga dashboard (open, oldest per step); `/payments/{id}/saga`; recovery
    held/actions; manual-review queue.
19. **Financial drift detection:** reconciliation every 5 min, nine invariants, confirmed after two runs, page on
    CRITICAL.
20. **Uncertain settlements:**
    - UNKNOWN is never a decline; same-key retry comes first.
    - Then MANUAL_REVIEW with a live rail inquiry.
    - The operator can RESUME, or CONFIRM_NOT_SETTLED after voiding the key at the rail.
21. **Safe shutdown:** readiness DOWN, preStop, graceful web drain, listeners finish the current record, relay
    batch allowed to finish; offsets are committed only after the business transaction (F-21).
22. **Zero Trust gaps remaining:** Kafka TLS, one Kafka identity, dev-mode Keycloak, `.env` secrets, no four-eyes
    approval, unauthenticated rail-simulator admin API (lab only).
23. **Operational risks remaining:** P-1, P-3, O-2 (reduced to IT-only rows), a Docker-flaky lab.
24. **Architectural trade-offs remaining:**
    - A single relay per outbox (ordering over throughput).
    - Reconciliation crosses schemas.
    - Rate limits are per instance.
    - The monolith shares CPU and pool between acceptance and processing.
25. **For 100×:** extraction with per-service databases, CDC or sharded relays, a sharded ledger with sub-accounts,
    a multi-broker Kafka cluster with hundreds of partitions, cells, and regional idempotency.
26. **What can be confidently defended?**
    - Where it breaks, why, and how that was measured.
    - That overload now degrades predictably instead of collapsing.
    - That money is never moved on a guess.
    - That every tuning claim has a same-conditions A/B behind it, including the two that were rejected.
