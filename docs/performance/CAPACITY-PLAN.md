# Capacity plan (WP-03)

> A **model with stated assumptions**, calibrated against lab measurements on one 8-vCPU host. It says where to
> look and what to change first. It is not a guarantee of production capacity.

## 1. Measured unit capacities (lab, per unit)

| Unit | Capacity | Limited by | Evidence |
|---|---|---|---|
| Outbox relay, one outbox, pipelined | **≈ 5,500 events/s** ≈ 690 payments/s of payment-outbox rows | single writer, broker round trips | `relay-benchmark-wp03-pipelined` |
| Outbox relay, WP-02 sequential | 106–118 events/s ≈ 13–15 payments/s | per-row synchronous ack | `relay-benchmark-*-sequential*` |
| Acceptance (HTTP only, async work throttled) | ≈ 375 payments/s at p99 < 300 ms | app CPU (2 vCPU) | `stress-baseline`, minute table |
| Acceptance with the full saga running in the same process | ≈ 180 payments/s accepted with bounded intake | shared CPU and 20-connection pool | `stress-g1-limit48` |
| Completion (saga end to end), one instance, concurrency 3 | **≈ 49 payments/s** sustained drain | consumer threads (3 per group) × service time (9–31 ms) | drain after `stress-g1-nolimit` |
| PostgreSQL (2 vCPU, 1 GiB) | ≈ 1,500–1,800 commits/s observed at saturation | CPU | `stress-*` |
| Kafka single broker (2 vCPU) | not saturated (≤ 5,500 msgs/s in; ack p99 ≤ 14 ms) | – | relay benchmark |

**Sustainable throughput of one instance = min(acceptance, completion) ≈ 45–50 payments/s** within the SLOs,
with completion the binding constraint. The assumed normal load (20/s) and peak (50/s) are inside it: see
TUNING-RESULTS.md §2 for the measured SLO status per scenario.

**Saturation point:** offered load above ≈ 50 payments/s is shed. In-flight limit and consumer-lag admission
return 503 + Retry-After; the system stays up, accepted payments complete, and nothing is lost.

## 2. Model per target payment rate R (payments/s)

| Quantity | Formula | R = 20 (normal) | R = 200 (10×) | R = 2,000 (100×) |
|---|---|---:|---:|---:|
| API requests/s | R × 1.5 (writes + 0.5 status reads) | 30 | 300 | 3,000 |
| Kafka messages produced/s | R × 12 | 240 | 2,400 | 24,000 |
| Kafka messages consumed/s | R × 10 | 200 | 2,000 | 20,000 |
| Payment-outbox rows/s | R × 8 | 160 | 1,600 | 16,000 |
| PostgreSQL transactions/s | ≈ R × 28 (measured: 563 commits/s at 20/s) | ≈ 560 | ≈ 5,600 | ≈ 56,000 |
| Outbox rows/day (all outboxes, before purge) | R × 12 × 86,400 | 20.7 M | 207 M | 2.07 B |
| Kafka storage/day (≈ 1 KB/msg, RF 3) | R × 12 × 1 KB × 86,400 × 3 | 62 GB | 622 GB | 6.2 TB |
| Kafka retention impact (7 d) | × 7 | 435 GB | 4.4 TB | 44 TB |
| Network into the brokers (produce, RF 3) | R × 12 × 1 KB × 3 | 0.7 MB/s | 7 MB/s | 72 MB/s |
| Required consumer parallelism per group | ≥ (R × events per group) × service time | ≈ 2 | ≈ 20 | ≈ 200 |
| Partitions per topic | ≥ max consumer parallelism of any group, with headroom | 6 | 24 | 256 (or split topics) |
| PayFlow instances (completion-bound, ~49/s each at concurrency 3) | R / 45 (10 % headroom) | 1 (lab), 2 for availability | 5 | 45: not the right architecture (see §4) |
| Connections | instances × pool | 2 × 20 = 40 | 5 × 20 = 100 | needs PgBouncer and per-service databases |

Assumptions:
- ≈ 12 messages per successful payment (WORKLOAD-MODEL §4).
- 1 KB envelopes; 7-day retention.
- Status reads per write: 0.5.
- DB transactions per payment: 28, taken from the measured commit rate (it includes inbox claims, relay batches
  and monitoring queries).
- Linear scaling of stateless instances, which **must be verified by test**: the lab has one host.

## 3. What happens at 10× (R = 200)

| Component | Verdict at 10× | Change needed |
|---|---|---|
| Relay | 1,600 rows/s against ≈ 5,500/s capacity: **fits** (≈ 30 % utilisation) | none; watch outbox age |
| Consumers | 5 instances × 3 = 15 consumers per group with 6 partitions: 9 idle | **24 partitions** (plan before launch: re-partitioning remaps keys) |
| PostgreSQL single primary | ≈ 5,600 tx/s: beyond the lab box, plausible for an 8–16 vCPU primary with NVMe | a bigger primary, PgBouncer, outbox partitioning by day, autovacuum tuning for outbox/inbox churn |
| Hot accounts | unchanged per account, whatever the scale | per-merchant rate tiers; sub-accounts for known hot payers |
| Reconciliation | the full ledger scan grows linearly | incremental (watermark per day) before 10× data volume |
| Observability | trace volume ×10 (Tempo OOM seen at ~400 req/s with 10 % sampling) | collector with tail sampling; lower head sampling |

## 4. What changes at 100× (R = 2,000)

Tuning does not get there; the architecture must change:
- **Extract** the contexts, each with its own database. The single primary at ≈ 56,000 tx/s is the wall.
- **CDC outbox** (Debezium) or **sharded relays**: 16,000 rows/s from one outbox exceeds a single relay.
- **Account-sharded ledger and balances**, **sub-accounts** for hot payers.
- Kafka: a multi-broker cluster, 256+ partitions or per-domain topic splits, tiered storage for retention.
- Cell-based deployment (bounded blast radius), regional idempotency stores, read replicas for status reads.

## 5. Autoscaling signals

| Signal | Scales what | Verdict |
|---|---|---|
| CPU (HPA, 60 %) | the stateless acceptance path | **enabled** in `deploy/k8s/base/hpa.yaml`; valid because acceptance is CPU-bound (measured) |
| Consumer lag (KEDA) | consumer throughput, **up to replicas × 3 = partitions** | evaluated, not enabled: beyond partitions it adds idle consumers; add it together with the partition increase |
| Outbox age / backlog | nothing: one relay per outbox (advisory lock) whatever the replicas | not a scaling signal; it is an admission and alerting signal |
| Request rate | acceptance | redundant with CPU |

**What does not scale horizontally:**
- The relay per outbox (by design: ordering).
- One hot account's row lock.
- The single PostgreSQL primary.
- Consumer parallelism beyond the partition count.

## 6. CI performance gates

`performance/scripts/ci-gate.py` checks a run's `report.json`:

| Level | Checks |
|---|---|
| PR | smoke: p95 < 500 ms, checks > 99 %, no DLT |
| Nightly | normal/peak: A1, A2, C1, P1 SLOs, no DLT, no dropped iterations; relative regression vs a reference run (p99 +20 %, throughput −15 %) |

Tolerances are deliberately loose (shared runners are noisy). Weekly soak and stress runs are reviewed as trends,
not gated.

## 7. Comparison with measurements

| Estimate | Measured | Note |
|---|---|---|
| Relay needs R × 8 rows/s | baseline relay 118 ev/s → ceiling ≈ 15 payments/s; measured completion ≈ 10/s | the relay thread also serves two other outboxes |
| PostgreSQL ≈ R × 28 tx/s | 563 commits/s at 20 payments/s (28.2 per payment) | used to calibrate |
| One instance ≈ 49 payments/s completion | peak (50/s) completes within the SLO; stress beyond it is shed | TUNING-RESULTS §2 |
