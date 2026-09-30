# Tuning results (WP-03): every change, measured

Rules:
- One change at a time.
- Same host, same load profile, same observability overhead.
- **A fresh database per compared scenario** (the baseline protocol).
- Numbers come from `performance/results/<run-id>/report.json`. Nothing here is estimated.

Why a fresh database matters, and was learned the hard way: final runs executed on a database that had grown to
7 GB over the day showed acceptance p99 of 103 ms and 353 ms, versus **48 ms** and 372 ms on a fresh database. The
difference was data growth (§3), not the change being measured. Those runs are kept as `*-growndb` and are used only
as evidence for §3.

## 1. Sequence of changes

| # | Change | Why (evidence) | Where |
|---|---|---|---|
| 1 | **Pipelined, draining outbox relay** | relay latency-bound at 118 ev/s (BOTTLENECK-ANALYSIS §1) | ADR-019 |
| 2 | **Recovery holds while commands are unpublished** | re-issues amplified a backlog (+23 %) | SAGA-DESIGN |
| 3 | **HTTP boundary classifies infrastructure failures** (503 + WARN, not 500 + ERROR) | 20,631 misclassified 500s with stack traces in one stress run | EXCEPTION-ARCHITECTURE |
| 4 | **G1 explicitly** | SerialGC (ergonomic below 1792 MiB) paused up to 38.6 s | Dockerfile, compose, ConfigMap |
| 5 | **In-flight bulkhead (48) on payment acceptance** | unbounded virtual threads: 1,292 requests waiting on the pool; heap filled | `TrafficControlInterceptor` |
| 6 | **Admission on consumer lag** (plus outbox age) | after (1) the backlog moved from the outbox to consumer lag; the outbox-age signal went blind | same |
| 7 | **Outbox retention 7 d → 1 h, batched purge; inbox purge scheduled (8 d)** | the payment outbox grew to 3.6 M rows / 3.8 GB, bigger than database memory; the inbox purge had never been scheduled | `OutboxRelay`, `InboxPurgeJob` |
| 8 | Consumer concurrency 6 → **kept at 3** (rejected change) | 6 exhausted the pool at normal load: acceptance p99 48 → 238 ms | compose, ConfigMap |

## 2. Final result vs baseline (fresh database each; one instance, 2 vCPU / 1.5 GiB)

| Metric | normal base → final | peak base → final | burst base → final | stress base → final |
|---|---|---|---|---|
| Offered | 20/s | 20→50/s | 20/s + spikes of 150/s | ramp to 400/s |
| Accepted/s | 20.0 → 20.0 | 45.2 → 45.2 | 44.3 → 44.2 | 180.3 → **65.0** (rest shed: 74,406 × 503/429) |
| Dropped iterations (generator) | 0 → 0 | 0 → 3 | 0 → 0 | 4,669 → **4** |
| **Acceptance p50 / p99** | 7.4 / 36.3 → 11.7 / **47.9 ms** | 7.0 / 37.8 → 10.8 / **372 ms** | 6.8 / 153 → 20.9 / **268 ms** | 7.3 / 866 → 0.9 / **235 ms** |
| **Completion p50 / p99** | 404 s / 548 s → 1.6 s / **2.1 s** | 311 s / ≥ 600 s → 1.7 s / **22.2 s** | 467 s / ≥ 600 s → 75 s / **136 s** | 186 s / ≥ 600 s → 129 s / **252 s** |
| Payments completed in window | 12,042 → 12,075 | 3,839 → **24,488** | 2,806 → **16,841** | 2,111 → **38,892** |
| Unfinished after the drain window | 0 (519 s) → 0 (**31 s**) | 20,518 → **0** (30 s) | 14,143 → **0** (30 s) | 105,306 → **0** (94 s) |
| Outbox publish delay p99 | 158 s → **0.34 s** | 441 s → **0.36 s** | 362 s → **0.60 s** | ≥ 600 s → **0.51 s** |
| Oldest outbox event (max) | 145 s → 0 | 395 s → 0 | 333 s → 0 | 623 s → 0 |
| Consumer lag max (main topics) | 4 → 6 | 1 → 466 | 41 → 3,217 | 11 → 9,571 |
| Pool active / pending max | 5 / 0 → 12 / 0 | 8 / 0 → 20 / 9 | 15 / 0 → 20 / 22 | 20 / 286 → 20 / 48 |
| Pool acquire max | 4.5 ms → 9.2 ms | 4.7 ms → 1.8 s | 183 ms → 393 ms | 8.0 s → 1.3 s |
| PostgreSQL commits/s | 404 → 563 | 608 → 1,244 | 595 → 1,222 | 1,789 → 1,604 |
| GC pause max / total | 190 / 3,976 → **22 / 1,392 ms** | 211 / 3,920 → 46 / 2,641 ms | 210 / 3,250 → 42 / 1,899 ms | **4,291** / 22,284 → **47** / 5,100 ms |
| Heap used max | 171 → 199 MB | 171 → 221 MB | 169 → 240 MB | 366 → 275 MB |
| Process CPU avg (of 2 vCPU) | 32 % → 38 % | 39 % → 62 % | 41 % → 69 % | 63 % → 85 % |
| DLT | 0 → 0 | 0 → 0 | 0 → 0 | 0 → 0 |

Reading it:
- **The system now does the work it accepts.** At normal and peak load completion fell from minutes to seconds
  (normal p99 548 s → 2.1 s; peak ≥ 600 s → 22 s, inside the 30 s SLO). Completed payments per window rose 6–18×.
- **More real work costs more resources.** CPU, pool usage and commits roughly doubled, because the saga pipeline
  actually runs now. That is also why acceptance p50/p99 rose at normal load (36 → 48 ms p99, still 6× under the SLO).
- **Overload is now predictable.** Under stress the baseline accepted 180/s, left 105,306 payments unfinished and
  stopped the world for 4.3 s. The final build accepts what it can finish (≈ 65/s), rejects the rest immediately with
  503/429 + Retry-After (a p50 of 0.9 ms for rejections), keeps acceptance p99 at 235 ms, and finishes every accepted
  payment 94 s after the load stops.
- **Still open:** (a) completion p99 under bursts and stress (136 s, 252 s): intake shedding reacts to lag only every
  15 s, and a threshold of 5,000 records per topic admits several minutes of work; (b) acceptance p99 at peak on one
  instance (372 ms, SLO 300 ms), see §5.

## 3. Data growth: the soak-type effect found mid-campaign

| Same final build, same peak profile | Acceptance p99 | Completion p99 | Pool pending max |
|---|---:|---:|---:|
| fresh database | 372 ms | 22.2 s | 9 |
| database after a day of runs (payment outbox 3.6 M rows / 3.8 GB, ≈ 7 GB total, DB container 1 GiB) | 353 ms (G1) / 763 ms (Serial) | 44.1 s (G1) / 60.8 s (Serial) | 38 |

Cause: published outbox rows were kept for 7 days (the events are also on Kafka for 7 days), and the inbox purge had
never been scheduled. At 20 payments/s that is ≈ 20 M outbox rows per day. Fix: 1 h outbox retention, batched
purges of 5,000 rows, and a scheduled inbox purge with 8 d retention (retention > Kafka retention is a correctness
requirement for deduplication). The soak run (§7) verifies the table sizes stay bounded.

## 4. Individual experiments

### 4a. Relay: same build, only the mode changed

| Mode | Isolated relay throughput |
|---|---:|
| WP-02 sequential (`pipelined=false`, `drain-budget=0`) | 106 events/s |
| WP-03 pipelined + drain | **5,552 events/s (× 52)** |
| (baseline image, PLAINTEXT Kafka, for reference) | 118 events/s (SASL costs ≈ 10 %) |

### 4b. Stress, WP-03 build, one variable at a time (grown database for all three, so comparable with each other)

| Config | Accepted/s | Acceptance p99 | 5xx | Pool pending | GC max pause |
|---|---:|---:|---:|---:|---:|
| SerialGC, unbounded intake | 90.9 | 10 s | 31.8 % | 1,292 | 38,564 ms |
| G1, unbounded intake | 148.6 | 5,436 ms | 12.8 % | 1,174 | 437 ms |
| G1 + in-flight bulkhead 48 | 179.4 | **442 ms** | 5.3 % (immediate 503s) | **52** | **71 ms** |

### 4c. Consumer concurrency (normal load)

| Concurrency | Acceptance p99 | Completion p99 | Pool active / pending | Acquire max |
|---|---:|---:|---:|---:|
| 3 | 48 ms | 2.1 s | 12 / 0 | 9 ms |
| 6 | **238 ms** | **16.2 s** | 20 / **16** | **1,481 ms** |

Concurrency applies to every listener container including retry topics: 6 roughly doubled the consumer threads
competing for 20 connections. **Rejected.** Throughput per instance is capped by the pool and CPU; add instances
instead (replicas × 3 ≤ partitions).

## 5. Peak on one instance: the acceptance-latency trade-off (fresh database)

| Config | Acceptance p99 | Completion p99 | GC max pause | Process CPU |
|---|---:|---:|---:|---:|
| G1 (final default) | 372 ms | 22.2 s | 46 ms | 62 % |
| SerialGC | **173 ms** | 20.3 s | 221 ms | see run |
| G1, **two instances** | **185 ms** | **4.0 s** | 29 ms | – |

- At peak (50/s) one 2-vCPU instance is at its completion capacity (≈ 49/s measured).
- On only 2 cores, G1's concurrent GC threads compete with request and consumer threads, which shows as tail
  latency. SerialGC gives a better p99 here, but its worst-case pause grows with live heap (38.6 s in the unbounded
  stress run).
- **Decision: keep G1.** A bounded worst case is worth more than a better p99 at peak on an undersized instance,
  and the production shape is two instances (`minReplicas: 2`). Recorded as MAJOR finding P-1 in the review.
- If one instance must meet the peak SLO: 3 vCPU per instance, or SerialGC with a hard heap cap, evaluated by the
  same test.

## 6. PostgreSQL plans (EXPLAIN ANALYZE, BUFFERS) on the grown database

See `performance/results/pg-explain-wp03.txt`:
- The relay's unpublished scan and the saga recovery scan use their **partial indexes** (index scans; sub-millisecond
  even with 3.6 M outbox rows, because the partial index holds only unpublished rows).
- The batched purge uses the primary key.
- No sequential scans on the hot path; no index was added without such evidence.

## 7. Hot account (per-subject limit raised to 1,000/s for the experiment; fresh database)

| Run | Accepted/s | Shed (503/429) | Acceptance p99 | Completion p99 | Pool pending max | Deadlocks | GC max |
|---|---:|---:|---:|---:|---:|---:|---:|
| baseline (`hot-account-baseline`) | 149.4 | 0 (81,852 unfinished after the window) | 314 ms | ≥ 600 s | 143 | 0 | 1,737 ms |
| final (`hot-account-final2`) | 62.5 | 50,363 | **208 ms** | 238 s | 44 | **0** | 63 ms |

PostgreSQL wait sampling during the final run (`hot-account-final2-lock-samples.txt`) shows `Lock:transactionid`
(row-lock waits on the hot `account_balance` row) in every sample. The contention the relay previously hid is now
visible, and intake control keeps it from collapsing the instance. The first final hot-account run was excluded:
the host suspended during it (a 9 s "GC pause"; data stops 60 s after the load), see `*-SUSPECT-host-sleep`.

## 8. Soak: 45 minutes at 20 payments/s (closes review O-1)

Run `20260929-130854-soak-final` (G1, listener concurrency 3, lag admission 5,000, 1 h outbox retention at the time).
53,997 payments were accepted, 3 k6 iterations were dropped (client side), and there were no throttles and no 5xx.

| Signal | Value | Verdict |
|---|---|---|
| Acceptance p50 / p99 (server) | 11.6 / 55.7 ms | flat for 45 min, SLO 250 ms |
| Completion p50 / p99 | 1.64 / 2.26 s | SLO met, no drift |
| Heap after GC, max | 94 MB (heap committed 192 MB) | no leak: the post-GC floor is flat |
| GC pause max / allocation rate | 41 ms / 87 MB/s | G1 steady state |
| Hikari pending / timeouts | 0 / 0 | pool not a constraint |
| Outbox publish delay p99 | 348 ms, backlog max 19 | relay keeps up |
| Consumer lag max | 32 records | |
| DLT / failed events / deadlocks | 0 / 0 / 0 | |
| Drain after load | 30 s, nothing left open | |

**Finding (fixed): outbox growth.** The 1 h retention never started purging inside a 45-minute run, so the outboxes
grew linearly (`performance/results/soak-table-sizes.txt`), and the payment outbox reached 436k rows / 477 MB. That is
about 1.9 GB/h per 20 payments/s before the first purge. The default retention is now **15 min**. Published rows are
only diagnostic by then, because the relay publishes within a second and re-delivery relies on consumer idempotency,
not on the outbox. `PAYFLOW_OUTBOX_RETENTION` overrides it. Proof (`performance/results/outbox-purge-proof.txt`): after
the first scheduled purge (every 5 min, 5,000-row batches), all three outboxes went from 435,584 / 109,096 / 54,448
rows to 0, and the payment table went from 477 MB to 41 MB. The inbox (`processed_event`, 8 d retention, about
30 MB/45 min) is bounded by design; its purge is covered by `InboxPurgeJob` tests.

## 9. Review P-2 follow-up: tighter lag admission (1,000 records, sampled every 5 s)

Fresh database per run; everything else as the final build.

| Scenario | Setting | Accepted/s | Acceptance p99 | Completion p99 | Drain |
|---|---|---:|---:|---:|---:|
| burst | 5,000 / 15 s (final) | 44.2 | 268 ms | 136 s | 30 s |
| burst | **1,000 / 5 s** | 30.5 | **1,484 ms** | 90 s | 31 s |
| stress | 5,000 / 15 s (final) | 65.0 | 235 ms | 252 s | 94 s |
| stress | **1,000 / 5 s** | 53.6 | **129 ms** | **62 s** | 31 s |

- **Under sustained overload the tighter threshold is clearly better:** acceptance p99 −45 %, completion p99 −75 %,
  a 3× faster drain.
- **Under bursts it is worse.** The 30-second time series of `burst-p2tuned` shows why:
  1. At the start of a spike lag is still low, so admission lets 81–95 payments/s through.
  2. Lag jumps and admission closes (≈ 58 × 503/s).
  3. The consumers drain and admission reopens.
  4. The admitted batches hit a CPU-saturated instance: p99 of 201 responses up to 4.1 s.

  That is bang-bang control on a sampled, lagging signal.
- **Decision:** keep 5,000 (bursts are the common overload shape) and 5 s sampling. P-2 stays open with a better
  diagnosis. A threshold cannot fix it; a smooth controller can, for example an adaptive concurrency limit (AIMD on
  the in-flight bulkhead driven by lag or latency) or token-bucket admission sized from the measured drain rate.

## 10. Review R-2: park settlements while the rail's circuit is open

**Problem (F-07, rail slowed to 1.7 s for 90 s):** the slow-call rate opens the rail's circuit. Every settlement
command then fails fast as CIRCUIT_OPEN (NOT_SENT), goes through the retry topics to the DLT, and waits for saga
recovery to re-issue it. Money is safe, but DLT alerts fire for a known outage and recovery is slow.

**Change:**
- A circuit-open failure **parks** the settlement. It stays PENDING, the evidence is recorded, and the message is
  acknowledged, with no retry topic and no DLT.
- `ParkedSettlementResumer` re-submits parked settlements per rail at a bounded rate. The claim uses
  `FOR UPDATE SKIP LOCKED` plus an idle window, so replicas never re-submit the same one at once. A rail's backlog
  does not block the other rails.
- Parked settlements have **one driver**, the resumer. A repeated command (a saga re-issue or a redelivery) returns
  "parked" without calling the rail.
- Kill switch: `PAYFLOW_SETTLEMENT_PARKING=false` restores fail-and-retry.

**Iterations** (evidence in `performance/invalidated/*-R2-*`; old Rancher host, so not comparable in absolute terms):
1. **Unbounded drain.** Recovery was fast, but about 2,200 resumed settlements, plus their capture and ledger work,
   hit at once. The pool had 47 waiting (4.1 s acquire) and acceptance p99 was 2.9 s.
2. **20/s per rail.** There were still 31 waiting and acceptance p99 was 2.5 s.
3. **5/s per rail.** There was no pool wait, but saga re-issues (868) drove parked settlements through the listener,
   **bypassing the budget**. That's why a parked settlement now has a single driver.

**Final A/B, same WSL host (6 vCPU, shared with a k3d cluster), 3 min at 20/s, same fault:**

| F-07 | Parking off (WP-03) | Parking on, 5/s per rail | **Parking on, 10/s per rail (default)** |
|---|---|---|---|
| Evidence (`performance/results/`) | `20260929-185314-*` | `20260930-033616-*` | `20260930-034525-*` |
| Dead-lettered commands | 1,526 | 0 | **0** |
| Circuit-open rail calls (wasted) | 7,118 | 1,886 | **1,801** |
| Saga re-issues (no-ops when parked) | 1,488 | 1,203 | **462** |
| Time to quiet after recovery | 376 s | 336 s | **178 s** |
| Completion p99 | 361 s | 363 s | **202 s** |
| Acceptance p99 | 242 ms | 28 ms | **183 ms** |
| Pool pending max / acquire max | 0 / 66 ms | 0 / 4.7 ms | 0 / 104 ms |
| Alerts | DeadLettersAppearing, DeadLetterSpike | PaymentCompletionSlow | PaymentCompletionSlow |
| Data safety (reconciliation, double capture/journal, SETTLED without rail completion) | clean | clean | clean |

- **Decision:** 10/s per rail. It halves recovery time and completion p99, and it removes every dead letter, so the
  only alert left is the one that describes the customer impact. Acceptance p99 stays inside the SLO and better than
  without parking.
- 5/s protects acceptance most (28 ms), but completion is then limited by the resume budget and is no better than
  without parking.
- The budget is per instance and per rail. With N replicas the total resume rate is N × 10/s per rail, which matches
  the N × completion capacity.

**Measurement note:** the first parking-on run after a host shutdown was invalidated (`*-contaminated-overnight-backlog`).
Payments parked overnight completed during its window: 4,825 completions for 3,606 accepted, and completion p99 at
the 10-minute histogram cap. Always check the database for open sagas before a run on a freshly restarted stack.

## 11. Review P-2: a work-in-progress window replaces lag-threshold admission

**Problem:** under bursts and sustained overload, admission let in more than the pipeline could finish. Accepted
payments then queued for minutes (completion p99 113 s burst, 215 s stress on this host), while the SLO is 99 %
within 30 s. The lag threshold (§9) could not fix it: a gate on a sampled, lagging signal oscillates.

**Change:** admission is bounded by **work in progress**, the payments whose saga waits on PayFlow's own processing
(risk, funds, capture, compensation). Settlement waits on an external rail with its own protection, and manual review
waits on a human, so neither counts. By Little's law, completion time = work in progress / throughput, so bounding
the work in progress bounds completion time whatever the offered load.
- The signal is a count over a subset of `ix_payment_saga_in_flight`, read at most every 250 ms per replica.
- Admission is a **window**: each 250 ms sample grants half of the free room (`limit - work in progress`) as credits.
  Excess requests get 503 `PAYMENTS_BUSY` with `Retry-After: 2`. Admissions can never outrun the room, and half the
  room keeps two replicas from jointly overshooting.
- New metrics: `payflow_traffic_admission_work_in_progress`, `payflow_traffic_admission_credits`, and
  `payflow_traffic_rejected_total{policy="work-in-progress"}`.
- The lag gate is off by default (the property remains); with the window, consumer lag stayed under 250.

**Iterations** (all on the WSL host, fresh stack per run, 90 s warm-up):
1. **Soft ramp** (shed linearly from 80 % to 100 % of 500, sampled every second). Completion improved (burst p99
   21 s), but accepted payments got slower: burst p99 of 201 responses was 1,118 ms, client max 5.5 s. The time
   series showed the admit ratio at 0 % while 27–63 payments/s were still being accepted. At 150 requests/s, one second
   of full admission overshot the whole 100-payment ramp, which is bang-bang again. Evidence:
   `performance/invalidated/*wip500-wsl-soft-ramp-oscillated`.
2. **Window, limit 500.** Stable (work in progress 466–509 during bursts), but burst completion p99 was 30.9 s,
   just outside the SLO, and each burst's first window admitted a ~234-payment slug.
3. **Window, limit 300** (the default).

| | Lag 5,000 (WP-03) | Window 500 | **Window 300 (default)** |
|---|---|---|---|
| Evidence (`performance/results/`) | `*-burst/stress-lag5000-wsl` | `*-burst/stress-window500-wsl` | `*-burst/stress-window300-wsl` |
| **Burst** completion p50 / p99 | 69 s / 113 s | 1.8 s / 30.9 s | **1.8 s / 18.7 s** |
| Burst acceptance p99, server / client | 344 / 388 ms | 213 / 288 ms | **116 / 151 ms** |
| Burst accepted / shed | 41.8/s / 80 | 23.6/s / 7,218 | 25.1/s / 6,660 |
| Burst pool waiting max, consumer lag max | 10, 2,895 | 32, 321 | **5, 186** |
| **Stress** completion p50 / p99 | 112 s / 215 s | 6.7 s / 22.1 s | **5.8 s / 25.0 s** |
| Stress acceptance p99 | 132 ms | 58 ms | 84 ms |
| Stress accepted throughput | 68.3/s | 64.0/s | 52.1/s |
| Stress drain after load, consumer lag max | 96 s, 7,800 | 32 s, 347 | **32 s, 216** |

- **Decision: 300.** It is the only setting that meets both SLOs (acceptance p99 < 300 ms, completion p99 < 30 s) in
  both scenarios. Bursts are the common overload shape.
- **The cost** is throughput under sustained overload: 52/s instead of 64–68/s. More payments get a fast 503 instead
  of being accepted and then waiting minutes. That is the intended trade: refuse cleanly rather than accept work that
  cannot finish in time.
- A limit around 400 may recover part of that throughput; it is the next tuning run.
- The limit is global (the database count), so with N replicas it should scale with N: roughly
  limit ≈ total throughput × target completion time.

