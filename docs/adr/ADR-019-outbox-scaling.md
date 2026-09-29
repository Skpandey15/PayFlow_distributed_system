# ADR-019: Scale the outbox relay by pipelining and draining, keeping the single writer (not CDC, not multiple writers)

- Status: Accepted (WP-03, resolves WP-02 K2)
- Date: 2026-09-27
- Evidence: WP-03-BASELINE.md, BOTTLENECK-ANALYSIS.md, TUNING-RESULTS.md, `performance/results/relay-benchmark-*`

## Context: measured, not assumed

WP-02 predicted (K2) that the polling relay would be the first bottleneck at 10× load. WP-03 measured it before
changing anything, and it was worse than predicted: it is the bottleneck at the **assumed normal load (1×)**.

| Evidence (baseline, WP-02 code) | Value |
|---|---|
| Isolated relay benchmark, payment outbox, 20,000 rows | **118 events/s** |
| Payment-outbox rows per successful payment | 8 (WORKLOAD-MODEL §4) |
| Resulting completion ceiling | 118 / 8 ≈ **15 payments/s** (≈ 10/s in the full pipeline, where the relay also serves two other outboxes in the same scheduler thread) |
| Normal load, 20 payments/s for 10 min | acceptance p99 36 ms (fine); **payment-outbox publish delay p99 158 s**; completion p99 **548 s**; 519 s to drain after the load stopped |
| At the same time | consumers p99 ≈ 30 ms, consumer lag ≈ 0, account and settlement outboxes < 1 s, DB pool 5/20 active, app CPU 31 % |

The relay is **latency-bound, not resource-bound**. Per row it does one `send().get()`: the broker ack takes ~5 ms,
with linger.ms = 5 paid on every lonely record. Then one `UPDATE`, and after a full batch of 100 a 200 ms poll
sleep. Nothing else in the system is busy.

A second, emergent effect was observed: overdue sagas were re-issued **into the same backlog** (1,275 extra
commands during one drain), a feedback loop. It is fixed separately by making recovery hold while commands are
unpublished (see SAGA-DESIGN and TUNING-RESULTS).

## Options evaluated

| Option | Throughput | Ordering | Duplicates | Failure windows | Complexity / ops |
|---|---|---|---|---|---|
| **A. Optimized polling:** drain full batches back to back (no poll sleep), one UPDATE per batch | removes the poll sleep: small gain alone | unchanged | unchanged | unchanged | trivial |
| **B. Asynchronous batched publication:** hand the whole batch to the producer, await all acks once | bounded by broker round trips per *batch* instead of per *row*; linger.ms now batches | per-partition order kept by the idempotent producer's sequence numbers (a later record of a partition cannot be written after an earlier one failed transiently); only acked rows are marked | same as today (crash after ack → re-send) | a record-level permanent error on an earlier record of the same key could let a later one through; mitigated (bounded envelope size; stuck row → outbox-age alert) | small (one method) |
| C. Multiple partition-aware publishers (shard the outbox by `hash(key) mod N`, one advisory lock per shard) | ~N × (A+B) | per key kept (a key maps to one shard) | same | same per shard | medium: shard column/index, N schedulers, rebalancing shards across replicas |
| D. Multiple writers with `FOR UPDATE SKIP LOCKED` | high | **breaks per-key order** unless rows are grouped by key, which then becomes option C | same | rows can publish out of order across writers | medium |
| E. CDC (Debezium on the WAL) | highest, with near-zero DB polling load | WAL order | Debezium is at-least-once too | connector restarts, replication slot growth can fill the disk if the connector stalls | **high:** Kafka Connect cluster, replication slots, connector upgrades, schema-change handling; a new operational team skill |

## Decision

Implement **A + B**, keep the **single writer per outbox** (advisory lock), and make each mode switchable
(`payflow.messaging.outbox.pipelined`, `drain-budget`) so the comparison is measured on the same build:

- `sendPipelined`: all rows of the batch → `kafka.send` (async); then await acks within one send-timeout budget;
  `UPDATE … WHERE id = ANY(?)` for acknowledged rows; the first failure is recorded (`publish_attempts`,
  `last_error`); failed rows stay unpublished and lead the next scan (id order).
- Drain loop: while a batch comes back full, publish the next immediately, for up to 1 s per outbox per tick, so
  one busy outbox cannot starve the others.

**C and E are not implemented, deliberately.** The decision rule is evidence: adopt C when the measured pipelined
ceiling is below 2× the peak-load requirement; adopt E when the polling load on PostgreSQL (or the ceiling after C)
becomes the constraint. The measured result is in TUNING-RESULTS.md, and the 10×/100× projection in CAPACITY-PLAN.md.

## Consequences

**Ordering:**
- Per partition: guaranteed by the idempotent producer (`enable.idempotence=true`, `max.in.flight=5`) plus
  id-ordered hand-over plus marking only acked rows.
- Across topics: never guaranteed (unchanged). Consumers stay order-tolerant (saga step guards).

**Duplicates:** unchanged (a crash after ack but before the UPDATE commit re-sends; the inbox absorbs it).

**Relay transaction:** now spans one round of acks instead of 100 sequential round trips. Still bounded by the
send timeout (5 s) and a 30 s transaction timeout.

**Operational:**

| Setting | Values |
|---|---|
| Relay mode | `PAYFLOW_OUTBOX_PIPELINED` |
| Drain budget | `PAYFLOW_OUTBOX_DRAIN_BUDGET` |
| WP-02 behaviour for comparison | `false` / `0s` |

Alerts and runbook: *Outbox backlog*.

**Failure implications:**

| Failure | Behaviour |
|---|---|
| Broker down | Every send in the batch fails after `max.block.ms` or `delivery.timeout.ms`; nothing is marked; unchanged semantics. |
| One partition leader unavailable | Other partitions keep publishing; that partition's rows retry in order. |
