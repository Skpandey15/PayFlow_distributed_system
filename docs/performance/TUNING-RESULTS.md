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

## 8. Soak: not executed

The 45-minute soak was **not run** in WP-03 (lab time and host instability). The growth findings in §3 and the
retention/purge fixes are verified by tests and table sizes, not by a long run. Recorded as MAJOR O-1 in the review.
