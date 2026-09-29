# WP-03 baseline: the WP-02 system, measured before any optimisation

> **What this is:** the unmodified WP-02 application (image `payflow:wp02-baseline` = WP-02 code plus WP-03
> **instrumentation only**: Prometheus endpoint, latency histograms, saga/outbox timers, lag monitor) in the
> pinned stack `performance/baseline/docker-compose.wp02-baseline.yml`. No tuning, no resilience changes.
> All later improvements are compared against these numbers.

## 1. Environment and method

| Item | Value |
|---|---|
| Host | one Windows 11 machine, Rancher Desktop VM: 8 vCPU, 10.7 GiB |
| PayFlow instance | 1 × (2 vCPU, 1.5 GiB); JVM defaults: `-XX:MaxRAMPercentage=75`, **SerialGC chosen ergonomically** (see §4) |
| Dependencies | PostgreSQL 18.1 (2 vCPU, 1 GiB), Kafka 4.2 KRaft single node (2 vCPU, 1.5 GiB, PLAINTEXT, 6 partitions), MongoDB 8.0 (1 vCPU), Keycloak 26.7 (unlimited) |
| Observability in the loop | Prometheus (5 s scrape), Tempo (10 % trace sampling, OTLP export on), JDBC spans, postgres-exporter |
| Noise on the host | k6 load generator, Keycloak token issuance, an unrelated k3d cluster (≈ 10 % of one core) |
| Load | k6 open model (WORKLOAD-MODEL.md); 90 s JIT warm-up before each campaign (not measured) |
| Windows | percentiles from server histograms over the exact load window; completion measured until quiet, or a bounded 342 s drain for overload runs (unfinished work reported) |
| Evidence | `performance/results/<run-id>/{report.md, report.json, k6-summary.json, docker-stats.csv}` |

**Measurement-integrity incident (disclosed).** On Windows, stopping the first campaign's task left its bash
children running, and it generated load concurrently with the reset-based campaign. Three runs were contaminated
(the server counted 77,783 accepted payments where k6 sent 16,691). They were moved to
`performance/invalidated/` and re-run. `run.sh` now refuses to start while another k6 container is running.
Normal and peak were verified clean (server-side accepted/s equals k6's within 1 %).

## 2. Results

| Metric | normal (20/s, 10 min) | peak (20→50/s) | burst (spikes 150/s) | hot-account (→300/s, one payer) | stress (→400/s) |
|---|---:|---:|---:|---:|---:|
| Offered iterations (k6) | 12,000 | 24,299 | 16,699 | 85,445 | 108,430 |
| Dropped iterations | 0 | 0 | 0 | 505 | 4,669 |
| Accepted/s (server, avg over window) | 20.0 | 45.2 | 44.3 | 149.4 | 180.3 |
| Acceptance 5xx ratio | 0 | 0 | 0 | 0.01 % | 0.3 % |
| **Acceptance p50 / p95 / p99** | 7.4 / 18.6 / **36.3 ms** | 7.0 / 18.6 / **37.8 ms** | 6.8 / 27.9 / **152.7 ms** | 7.3 / 43.4 / **314 ms** | 7.3 / 71.2 / **866 ms** |
| Acceptance max (client) | 456 ms | 266 ms | 415 ms | 7.3 s | 20.5 s |
| Status read p99 | 4.9 ms | 4.6 ms | 6.7 ms | 82 ms | 503 ms |
| **Completion p50 / p99** | 404 s / **548 s** | 311 s / **≥ 600 s** | 467 s / **≥ 600 s** | 230 s / **≥ 600 s** | 186 s / **≥ 600 s** |
| Payments unfinished after 342 s drain | 0 (drained in 519 s) | 20,518 | 14,143 | 81,852 | 105,306 |
| Outbox publish delay p50 / p99 | 58 s / 158 s | 125 s / 441 s | 112 s / 362 s | 184 s / 547 s | 202 s / ≥ 600 s |
| Payment-outbox backlog max | 13,672 | 40,981 | 32,580 | 158,746 | 204,333 |
| Account / settlement outbox delay p99 | 984 ms / 713 ms | < 1 s | < 1 s | < 1 s | < 1 s |
| Relay published events/s (all outboxes) | 128 | 125 | 121 | 114 | 109 |
| Broker ack wait p99 (per record) | 8.1 ms | 8.6 ms | 10.2 ms | 12.2 ms | 13.7 ms |
| Consumer lag max (main topics) | 4 | 1 | 41 | 32 | 11 |
| Consumer processing p99 (worst group) | 37 ms | 33 ms | 89 ms | 84 ms | 50 ms |
| Retry-category failures (CONCURRENCY) | 1 | 1 | 10 | 7 | 6 |
| DLT | 0 | 0 | 0 | 0 | 0 |
| Hikari active max / pending max | 5 / 0 | 8 / 0 | 15 / 0 | **20 / 143** | **20 / 286** |
| Hikari acquire max | 4.5 ms | 4.7 ms | 183 ms | **5.0 s** | **8.0 s** |
| Connection hold (avg usage) | 8.2 ms | 6.1 ms | 7.7 ms | 4.8 ms | 4.8 ms |
| PostgreSQL commits/s | 404 | 608 | 595 | 1,524 | 1,789 |
| Deadlocks | 0 | 0 | 0 | 0 | 0 |
| MongoDB command avg | 0.6 ms | 0.6 ms | 0.7 ms | 1.8 ms | 0.7 ms |
| JVM heap used max / committed max | 171 / 223 MB | 171 / 228 MB | 169 / 232 MB | 226 / 234 MB | 366 / 373 MB |
| **GC pause max** | 190 ms | 211 ms | 210 ms | **1,737 ms** | **4,291 ms** |
| GC pause total / collections | 4.0 s / 1,077 | 3.9 s / 1,012 | 3.3 s / 777 | 9.7 s / 1,613 | 22.3 s / 1,840 |
| Allocation rate | 67 MB/s | 84 MB/s | 84 MB/s | 147 MB/s | 165 MB/s |
| Live threads max | 191 | 188 | 190 | 189 | 189 |
| Process CPU avg / max (of 2 vCPU) | 32 % / 43 % | 39 % / 53 % | 41 % / 82 % | 58 % / 86 % | 63 % / 89 % |
| Container CPU avg (1 core = 100 %) | app 64, kafka 50, pg 22, mongo 11 | app 78, kafka 54, pg 27 | app 82, kafka 53, pg 28 | app 122, kafka 74, pg 56, keycloak 15 | app 132, kafka 65, pg 58, keycloak 27 |

Isolated relay benchmark (20,000 rows into the payment outbox, nothing else running): **118 events/s**
(`relay-benchmark-baseline-sequential-*.txt`).

Stress, minute by minute (offered rate → acceptance p99):

| Offered rate | 31 | 63 | 88 | 126 | 176 | 226 | 276 | 326 | 376/s | (ramp down) |
|---|---|---|---|---|---|---|---|---|---|---|
| Acceptance p99 | 20 ms | 28 | 16 | 19 | 130 | 226 | 108 | 32 | 144 | **10 s (4.3 s GC pause + 5xx)** |

## 3. What the baseline shows

1. **Acceptance and completion are two different systems.**
   - The synchronous path accepted up to ≈ 375 payments/s with p99 under the 300 ms SLO, until it saturated CPU
     and a stop-the-world GC collapsed it.
   - The asynchronous path **completed only ≈ 10–15 payments/s**, below the assumed *normal* load (20/s).
   - Measuring only `POST` latency would have declared the system healthy at 20× its real capacity.
2. **The first bottleneck is the payment-outbox relay (WP-02 K2), already at 1× load, not at 10×.**
   - It publishes 109–128 events/s whatever the offered load.
   - The payment outbox receives 8 rows per payment. The account and settlement outboxes (2 and 1 rows per
     payment) stay under 1 s.
   - Consumers, PostgreSQL, MongoDB and the broker are idle by comparison:

     | Signal | Value |
     |---|---|
     | Consumer p99 | ≤ 90 ms |
     | Consumer lag | < 50 |
     | Broker ack wait | 8–14 ms |
     | Pool at normal load | 5/20 |

   - Latency-bound: ~1 broker round trip per row, sequentially, plus a 200 ms sleep after every batch.
3. **Overload feeds on itself (metastable loop).**
   - Once commands wait in the outbox longer than the saga step timeout (30 s), recovery re-issues them into the
     same outbox. During one drain it added 1,275 commands (≈ 23 % extra traffic)
     (`baseline-metastable-drain-evidence.txt`).
   - Here it converged (all 5,434 payments completed) because re-issues are bounded to 3.
   - At higher backlog it would push payments into timeout rejections and manual review.
4. **The JVM configuration is a latent failure.**
   - The 1.5 GiB container makes the JVM pick **SerialGC** (G1 needs ≥ 1792 MiB to be chosen ergonomically).
   - The heap stays tiny (≈ 230 MB committed, 1,000+ collections per run).
   - Under stress, old-generation collections stop the world for **1.7 s and 4.3 s**. During those pauses
     connections are not returned, so the pool queue reaches 286 waiters, acquisitions time out after 3 s, and
     5xx responses appear.
5. **Hot-account contention is masked.** With one payer at up to 300/s, row-lock waits exist (a
   `SELECT … FOR UPDATE` max of 550 ms) but stay small. The relay lets reserve commands through at only ~15/s, so
   the lock is never contended hard. The upstream bottleneck hides the downstream one; it must be re-measured
   after the relay is fixed (BOTTLENECK-ANALYSIS §3).
6. **Idle cost of the retry topology.** The broker uses ≈ 50 % of a core with no load (idle measurement before
   the runs): about 30 retry/DLT listener containers, each with 2–3 consumers long-polling. That is an operational
   cost of WP-02's per-group retry topics (ADR-014), recorded here, and not changed in WP-03.
7. **The load generator shares the host.** Keycloak spiked to 7–12 cores (token refreshes) and 2.7 GB during
   hot-account and stress. Absolute numbers under stress are pessimistic for PayFlow. Comparisons remain fair
   (same conditions).

## 4. Baseline status against the proposed SLOs (SLI-SLO.md)

| SLO | Normal load | Verdict |
|---|---|---|
| A1 acceptance availability ≥ 99.9 % | 100 % | met |
| A2 acceptance p99 < 300 ms | 36 ms | met |
| C1 completion 99 % < 30 s | p99 548 s (≈ 0 % within 30 s) | **violated** (burn rate ≈ 100×) |
| P1 publication 99 % < 5 s | p99 158 s | **violated** |
| R1 financial integrity | 0 mismatches (reconciliation checked on the WP-03 build) | met |

**Conclusion:** the WP-02 system is correct but cannot sustain its assumed normal load. The WP-03 work is
prioritised by this evidence: (1) the relay, (2) recovery amplification, (3) the JVM collector, (4) re-measure and
find the next bottleneck.
