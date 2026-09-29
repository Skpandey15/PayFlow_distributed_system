# Bottleneck analysis (WP-03)

Method:
1. For every resource: utilisation, saturation, errors (USE).
2. For every hop: rate, errors, duration (RED).
3. The bottleneck is the component whose queue grows while its neighbours idle.
4. Prove it in isolation, change one thing, re-measure.

Bottlenecks mask each other, so after each fix the next one was re-discovered, not assumed.

Run ids refer to `performance/results/`. Final numbers are in TUNING-RESULTS.md.

## 1. Payment-outbox relay (WP-02 K2): found at 1× load, fixed

**Symptom.** At normal load (20/s), acceptance p99 was 36 ms, but completion p99 was **548 s**.

**USE / RED at 20 payments/s (`normal-baseline`):**

| Component | Utilisation | Queue |
|---|---|---|
| Payment-outbox relay | one thread, sequential | backlog 13,672 and growing linearly; publish delay p99 158 s |
| Account / settlement outboxes (same relay mechanism) | – | < 1 s |
| Consumers | p99 ≤ 37 ms | lag ≤ 4 |
| Kafka broker | ack wait p99 8 ms | – |
| PostgreSQL | pool 5/20 active, hold 8 ms, 404 commits/s | 0 pending |
| App CPU | 31 % | – |

**Why only the payment outbox:** it receives 8 rows per payment, versus 2 and 1 (WORKLOAD-MODEL §4).

**Proof in isolation:** 20,000 rows, nothing else running → **118 events/s** (`relay-benchmark-baseline-sequential`).
Per row: one synchronous `send().get()` (≈ 5 ms linger + round trip), one UPDATE, and a 200 ms sleep after each full
batch. It is latency-bound, so neither CPU nor replicas would help: the advisory lock admits one relay per outbox.

**Fix (ADR-019):** pipelined sends with one ack wait per batch, plus a drain loop, keeping the single writer.

**Same-build A/B:** 106 events/s (WP-02 mode) → **5,552 events/s** (×52). Normal-load completion p99 went
548 s → 2.1 s.

## 2. Saga recovery amplifying the backlog (metastable loop): found during a drain, fixed

**Symptom.** With **no incoming traffic**, the payment-outbox backlog *grew* (7,912 → 9,329).

**Cause.**
- Commands that sat unpublished longer than the step timeout (30 s) made the saga look overdue.
- Recovery re-issued them into the same outbox: 1,275 re-issues during one drain (≈ 23 % extra traffic)
  (`baseline-metastable-drain-evidence.txt`).
- Bounded retries (3) made it converge that time. With a larger backlog it turns into timeout rejections and
  manual-review escalations of payments that did nothing wrong.

**Fix:** recovery **holds** while the oldest unpublished command is older than 15 s
(`PaymentSagaServiceTest.recoveryHolds…`; `payflow.saga.recovery.held`).

## 3. Hot account: masked by (1), then measured

**Why it cannot scale out.**
- Every debit of one account must lock its `account_balance` row (`SELECT … FOR UPDATE` in `FundsService`) to keep
  the balance correct.
- The lock serialises those transactions whatever the number of pods: throughput per account ≤ 1 / lock-hold time.
- Replicas add waiters, not throughput.
- The Kafka key is the **paymentId**, so a hot account spreads its commands over all partitions; the contention is
  in PostgreSQL, not in Kafka.

**Baseline (`hot-account-baseline`):** the relay admitted reserve commands at ~15/s, so the row lock was never under
pressure (`SELECT … FOR UPDATE` mean 0.9 ms, max 550 ms). **The upstream bottleneck hid the downstream one.**

**After the relay fix:** measured in `hot-account-final`; numbers in TUNING-RESULTS.md §5.

**Improvements that keep correctness** (the ones not taken are recorded with reasons):

| Option | Effect | Correctness | Decision |
|---|---|---|---|
| Keep the locked transaction minimal (lock → check → update → outbox → commit) | lower hold time | unchanged | already the design (WP-02); the hold time is measured |
| Deterministic lock order in capture (payer, payee) | no deadlocks | unchanged | done in WP-02; **0 deadlocks** in all WP-03 runs |
| Per-subject rate limit | a hot *customer* cannot flood | unchanged | done (20/s default; a merchant tier would be higher) |
| Sub-accounts (balance split into N shards; reserve from any shard with funds) | ~N × throughput | same invariant per shard; the total is the sum | **not implemented**: needs domain design (rebalancing, overdraft across shards); first 100× lever |
| Batching reservations per account (group commit) | amortises the lock | preserved if batched in one transaction | not implemented (latency trade-off) |
| Optimistic locking with retry instead of FOR UPDATE | fewer blocked threads | preserved (version check) | rejected: under contention it converts waiting into retries and CPU burn |
| Weaker isolation or dropping the lock | throughput | **breaks overdraft protection** | rejected: correctness before throughput |

## 4. Shared CPU and connection pool between acceptance and processing: found after (1), fixed

**Symptom** (WP-03 build, SerialGC, `burst-wp03`, `stress-wp03`):
- Acceptance p99 was **worse** than in the baseline under bursts (568 ms vs 153 ms).
- Under stress it **collapsed**: 91 accepted/s, 32 % 5xx, p99 10 s.

**Cause.** In the baseline the slow relay throttled all asynchronous work, which left CPU and connections for
the API. Once the relay was fixed, the whole saga pipeline really ran, in the **same 2-vCPU process with the same
20-connection pool** as HTTP. Then:

| Step | Evidence |
|---|---|
| 1. HTTP requests run on unbounded virtual threads | – |
| 2. They piled up waiting for connections | **1,292 pending**, acquisition up to 43 s |
| 3. Each held request state, so the heap filled | committed 1.1 GB |
| 4. SerialGC (the ergonomic choice below 1792 MiB) stopped the world | **38.6 s** max pause, 107 s of pauses in 10 minutes |
| 5. Everything timed out | 20,940 pool timeouts |

**Fixes, measured one at a time on the same stress profile:**

| Step | Change | Accepted/s | Acceptance p99 | 5xx | Pool pending max | GC max pause |
|---|---|---:|---:|---:|---:|---:|
| (WP-03 build) | SerialGC, unbounded intake | 90.9 | 10 s | 31.8 % | 1,292 | 38,564 ms |
| a | **G1** | 148.6 | 5,436 ms | 12.8 % | 1,174 | 437 ms |
| b | G1 + **in-flight bulkhead 48** on acceptance | **179.4** | **442 ms** | 5.3 % (immediate, deliberate 503) | **52** | **71 ms** |

The bulkhead is Little's law applied: pool 20 × ~10 ms hold gives about 2,000 transactions/s of DB service at
most. Beyond ~2× the pool size in flight, extra requests only queue. Rejecting them immediately (503 + Retry-After)
keeps accepted requests fast and the heap bounded.

## 5. Where unfinished work accumulates after (1): admission signal corrected

With the relay fixed, the **outbox never ages any more**, so admission control keyed on outbox age stopped firing,
although completion was far behind: 67,086 open sagas after stress, drained at ~49/s. The unfinished work now
accumulates as **consumer lag**.

**Fix:** admission also closes when any group's main-topic lag exceeds 5,000 records (≈ 15–20 s of work at the
measured drain rates). This is a design correction found by measurement: *a backpressure signal must follow the
bottleneck, and the bottleneck moves.*

## 6. Consumer parallelism (partitions × concurrency)

**Drain after stress (`stress-g1-nolimit`):**
- 49 completions/s at 65 % CPU, pool 2/20 active, GC negligible, so not CPU-, DB- or GC-bound.
- Each group ran **3 threads for 6 partitions**. Per-event service time was ~9 ms for payment-service and ~31 ms for
  settlement-service (which includes the rail HTTP call).
- Throughput per group ≈ threads ÷ service time.

**Change:** listener concurrency is configurable. Lab (1 instance): 6 = partitions. Kubernetes (2 replicas): 3
(replicas × concurrency = partitions). Measured effect: TUNING-RESULTS.md §4.

**Consumers beyond partitions do nothing.** A second instance with concurrency 6 gives 12 consumers for 6
partitions: 6 are assigned nothing. Verified with `kafka-consumer-groups --describe --members` (F-15). Raising
partitions later adds parallelism but **remaps keys**, so per-payment ordering is not guaranteed across the change.
Size partitions for the target parallelism up front (CAPACITY-PLAN).

## 7. PostgreSQL

Evidence: `pg-stat-statements-wp03.txt` (all WP-03 runs), postgres-exporter, `EXPLAIN (ANALYZE, BUFFERS)` in
TUNING-RESULTS.md §6.

| Question | Finding |
|---|---|
| Slow queries? | None by plan. Every top statement has a mean < 1.2 ms and a buffer hit ratio ≥ 98.8 %. Multi-second **max** times occurred only during the SerialGC and pool-collapse episodes (§4): the client stalled, not the query. |
| Where does DB time go? | Writes: outbox inserts (≈ 12 per payment across three outboxes; one pg_stat_statements entry aggregates them), inbox claims, versioned saga/payment updates, ledger lines. This is the cost of exactly-once effects on an at-least-once bus. It is the price of correctness, not waste. |
| Missing indexes? | None found for the hot path (relay scan: partial index; saga recovery: partial index). One added in V9 for the manual-review queue (partial on MANUAL_REVIEW), justified by the monitoring query, which counted MANUAL_REVIEW outside the in-flight partial index. |
| Locks / deadlocks | 0 deadlocks in every run (deterministic lock order). Row-lock waits only on hot accounts (§3). |
| Pool | Healthy load uses 5–15 of 20 connections with a 5–8 ms hold. Saturation (§4) was caused by unbounded intake, not by pool size. Raising the pool would have added DB contention without adding throughput (see below). |
| Table growth | ≈ 12 outbox rows, 4–5 inbox rows and 2 ledger lines per payment. Purge: outbox published rows after 7 d, inbox after retention. At 20 payments/s that is ≈ 20 M outbox rows/day before purge. Autovacuum on the outbox (insert-then-update-once) is the main maintenance load; partitioning the outbox by day (drop partition instead of DELETE) is the 10× measure (CAPACITY-PLAN). |
| WAL | Not a constraint at lab rates (`WalSync` waits appeared only briefly under stress). At 10 × it becomes the single-primary limit. |

**Why not a bigger pool?** 20 connections at 5–8 ms hold serve 2,500–4,000 transactions/s. The lab PostgreSQL (2 vCPU
limit) reached its CPU limit under stress before the pool size mattered; docker stats samples even peaked above
200 % because CFS quota is enforced per 100 ms period. More connections
would add context switching and lock contention inside PostgreSQL.

Capacity rule: `replicas × pool ≤ max_connections − reserve`. In Kubernetes, 6 × 20 = 120 at maximum scale, which
needs `max_connections` ≥ 150 or PgBouncer.

## 8. JVM

| Observation | Evidence | Consequence |
|---|---|---|
| Ergonomics chose **SerialGC** in a 1.5 GiB / 2 vCPU container | `java -XX:+PrintFlagsFinal`: UseSerialGC ergonomic (G1 needs ≥ 1792 MiB and 2 CPUs to be selected) | young collections every ~0.5 s at load; stop-the-world old collections of 1.7 s / 4.3 s / 38.6 s under stress |
| G1 explicitly | stress: max pause 38.6 s → 437 ms → 71 ms (with bounded intake) | adopted in Dockerfile, compose and ConfigMap |
| Heap | `MaxRAMPercentage=70` of 1.5 GiB ≈ 1.05 GiB max; live data after GC ≈ 110–230 MB at steady load | the rest covers bursts; 30 % stays for metaspace (~140 MB), thread stacks, direct buffers (Kafka, HTTP), code cache |
| Allocation | 67 MB/s (normal) to 200 MB/s (stress); ≈ 3 MB allocated per accepted payment incl. its saga | JSON (Jackson 3) envelopes and JPA dominate. Not optimised: GC cost after G1 is < 1 % of CPU. |
| Threads | 74–191 live platform threads (Kafka consumers ×3–6 per container, scheduler, Hikari, Tomcat helpers); request handling on virtual threads | virtual threads remove thread-pool sizing but also remove the implicit concurrency limit, hence §4's bulkhead |
| Leak check | soak (`soak-final`): heap-after-GC trend and thread count, TUNING-RESULTS.md §7 | – |

**When further JVM tuning would be justified:** only with evidence of GC overhead > ~5 % of CPU or pause-time SLO
misses (heap sizing, region size, pause target), or of JIT warm-up affecting p99 after deploys (CDS/AOT cache).
Neither was observed after G1. **Harmful tuning** would be large fixed `-Xmx` equal to the container limit (native
memory OOM kill), `-XX:+UseParallelGC` for latency-sensitive services, or disabling explicit container support.

## 9. Kafka

| Question | Finding |
|---|---|
| Producer | `acks=all` + idempotence kept (durability first). Pipelining lets `linger.ms=5` actually batch: 5,552 events/s from one relay thread on a single broker. Compression not enabled: envelopes ≈ 1 KB and bandwidth is not the constraint. |
| Consumers | `max.poll.records=50` × ≤ 31 ms ≈ 1.6 s per poll loop, far inside `max.poll.interval.ms` (300 s). Parallelism is bounded by partitions (§6). |
| Lag | broker-side lag monitor; no lag at normal/peak after the fixes; bounded by admission (§5) under overload |
| Hot partitions | none: key = paymentId (uniform). Account hotness never reaches Kafka (§3). |
| Rebalance | cooperative-sticky; an instance join/leave moved only the affected partitions, and processing continued (F-15) |
| Broker idle cost | ≈ 50 % of a core with no load: ~30 retry/DLT listener containers long-polling. An accepted cost of per-group retry topics (ADR-014); reducing it (fewer retry levels, larger `fetch.max.wait.ms` on retry topics) is future work. |

## 10. MongoDB

The fraud store stayed at a 0.6–1.8 ms command average in every run (one indexed insert and one indexed velocity
count per payment). Not a bottleneck at lab scale. Its failure mode (unavailability, F-05) matters more than its
latency.
