# WP-03 Interview Defense: resilience, scale, production engineering

Answers are anchored in what PayFlow actually does and measured. Numbers come from `docs/performance` and
`performance/results`: the baseline in WP-03-BASELINE.md, the improvements in TUNING-RESULTS.md.

---

## SRE fundamentals

**What is an SLI?**
A measured ratio of good events to valid events that reflects user experience. PayFlow's A1 is "non-5xx
`POST /api/v1/payments` / all POSTs". It counts 503 admission shedding as bad and 429 as good (the caller's
quota), because the question is "did we refuse valid work?".

**What is an SLO?**
A target for an SLI over a window, e.g. A1 ≥ 99.9 % over 30 days. It is an internal engineering target, and it is
chosen from what the architecture can deliver (one PostgreSQL primary with failover ⇒ three nines, not four).

**What is an SLA?**
A contract with consequences (credits, penalties). It is always looser than the SLO, so you breach the SLO (and
react) before you breach the contract. PayFlow has none; our SLOs are internal.

**What is an error budget?**
1 − SLO: the unreliability we can afford. 99.9 % over 30 days = 43.2 minutes of total outage (≈ 43.8 min in an
average month). Its purpose is decision-making:

| Budget consumed | Response |
|---|---|
| 50 % | investigate every burn |
| 75 % | freeze risky payment-path releases |
| 100 % | feature freeze, reliability work first |

Financial-integrity incidents trigger the 100 % response regardless of budget (ERROR-BUDGET.md).

**p50 vs p95 vs p99?**
- p50 is the typical request.
- p95 is the tail most users meet sometimes.
- p99 is the severe tail. At 20 payments/s, 1 % is 12 payments per minute; and for a customer making 50
  requests per session, p99 is what they actually hit.
- We report all three, computed from **server histograms aggregated across replicas**, never averaged.

**Why is average latency misleading?**
Latency distributions are skewed and often multi-modal. In the PayFlow baseline at normal load:
- the acceptance **median was 7 ms** and p99 36 ms;
- an average would be ~9 ms and would hide the tail completely;
- worse, the *completion* latency was minutes while acceptance looked perfect.

That is why acceptance and completion are separate SLIs.

## Capacity and bottlenecks

**How do you determine system capacity?**
1. Define the workload (WORKLOAD-MODEL).
2. Drive it with an **open-model** generator (arrival rate fixed; no coordinated omission).
3. Ramp until an SLO breaks or a resource saturates.
4. Capacity is the highest rate that **sustains** the SLOs, not the highest rate the API accepts.

PayFlow's baseline "accepted" 50 payments/s at peak with a 38 ms p99, but it **completed** only ≈ 10/s. So its
capacity was ≈ 10 payments/s, below the assumed normal load.

**How do you find a bottleneck?**
- Use USE (utilization, saturation, errors) on every resource and RED (rate, errors, duration) on every hop, on
  one time axis.
- The component whose queue grows while its dependencies are idle is the bottleneck.

In the baseline:

| Resource | State |
|---|---|
| Consumers | p99 30 ms, lag 0 |
| DB pool | 5/20 |
| App CPU | 31 % |
| Broker ack | 8 ms |
| **Payment-outbox backlog** | **grew linearly** |

→ the single sequential relay. Then prove it in isolation: the relay benchmark measured 118 events/s.

**Scale up vs scale out?**
- Scale up adds resources to one instance: it helps CPU- or memory-bound work.
- Scale out adds instances: it helps only if the work is parallel and the shared bottleneck is elsewhere.

The PayFlow relay was *neither*: it was latency-bound and single-writer by design (advisory lock), so more CPU or
more pods would not have helped. Pipelining (ADR-019) did.

**What happens at 10× load?** (CAPACITY-PLAN.md has the arithmetic.)
200 payments/s means:
- 1,600 payment-outbox rows/s through one relay
- 2,400 Kafka messages/s
- ~2,000 PostgreSQL transactions/s
- consumers bounded by 6 partitions per topic

What breaks, in order:
1. The relay ceiling unless it is sharded (ADR-019 option C).
2. PostgreSQL write capacity on one primary (commit latency, WAL).
3. Partition-bounded consumer parallelism.
4. Hot accounts regardless of any of that.

**What happens at 100×?**
Architecture change, not tuning:
- Contexts extracted, each with its own database.
- Outbox via CDC (or sharded relays).
- Partitions sized for consumers (and planned before, since re-partitioning remaps keys).
- Ledger and balances sharded by account.
- Hot accounts split into sub-accounts (see below).
- Idempotency and read paths off the primary.
- Multi-region with explicit consistency choices.

## Resilience patterns

**What is backpressure?**
The consumer's inability to keep up is propagated to the producer, so the producer slows down, instead of work
queuing without bound until something falls over. PayFlow:
- Kafka is pull-based (natural backpressure).
- The outbox is a bounded-by-policy disk queue.
- **Admission control** returns 503 when the oldest unpublished event is older than 60 s.
- Saga recovery **holds** while the outbox is behind.

There is no unbounded in-memory queue anywhere.

**How do you prevent retry storms?**
- Retry only transient failures.
- Bounded attempts, exponential backoff, jitter.
- **Don't retry at every layer.** The rail adapter retries once, only on connection refused or 503, never on
  timeouts.
- A circuit breaker turns sustained failure into zero calls.
- Kafka retries are non-blocking with growing delays; saga recovery is paced (step timeouts) and holds on backlog.

We measured a related storm in the baseline: recovery re-issuing commands into a backlogged outbox added 23 % extra
traffic. The hold rule removes it.

**Timeout vs retry vs circuit breaker?**

| Mechanism | What it does |
|---|---|
| Timeout | bounds how long **one** call can take (protects the caller's resources) |
| Retry | tries again for a transient failure (improves success probability, costs load) |
| Circuit breaker | stops calling a dependency that is failing or slow (protects both sides; fails fast) |

Order: timeout first (without it nothing else works), then retry (bounded), then a breaker around them.

**What is exponential backoff? Why jitter?**
- Backoff: wait longer after each failure (200 ms, 400 ms, …) so a struggling dependency gets breathing room.
- Jitter: randomize each wait (±50 % in PayFlow) so hundreds of callers that failed together do not retry
  together (thundering herd).

**When should you NOT retry?**
- Validation and authorization failures, permanent business rejections (a decline), unsupported requests, 4xx
  answers: the same request cannot succeed.
- Anything with side effects that is **not idempotent**.
- Timeouts against a provider that is already slow: retrying amplifies its overload. PayFlow leaves those to the
  delayed Kafka retry.
- 429 (the provider asked you to slow down).

**Circuit breaker states?**

| State | Behaviour |
|---|---|
| CLOSED | calls flow; outcomes are recorded |
| OPEN | calls fail immediately, without touching the dependency |
| HALF_OPEN | a few probe calls are allowed; successes close it, failures reopen it |

PayFlow opens on 50 % failures or 80 % slow calls over the last 20 (minimum 10), stays open 15 s, and probes with
3 calls. Verified in `SettlementRailResilienceTest`.

**What is a bulkhead? Semaphore vs thread-pool?**
A bulkhead caps concurrency per dependency or workload so one cannot exhaust shared resources.

| Kind | How | Trade-off |
|---|---|---|
| Semaphore | limits concurrent calls on the caller's own thread | cheap, no hand-off |
| Thread-pool | runs calls on a dedicated pool | isolates the caller thread and allows timeouts on blocking code, but adds a queue and context switches |

PayFlow uses semaphores (callers are consumer threads or virtual threads), with separate compartments for
payment traffic (16) and operator inquiries (2) per rail.

**Why not put a circuit breaker around a database repository?**
- PostgreSQL is the authoritative store; there is no meaningful fallback. "Fail fast" is already provided by the
  pool timeout (3 s) and the transaction timeout.
- A breaker would turn a transient blip into a longer self-inflicted outage.
- It would hide real DB state from health checks and invite retries of half-completed transactional work.

The right tools for the DB are timeouts, a bounded pool, readiness and backpressure.

## Kafka

**How does Kafka handle backpressure?**
Consumers pull at their own pace; the broker stores the backlog durably; lag is the signal. Producers get
backpressure through `buffer.memory` and `max.block.ms` (PayFlow 5 s), and a relay that is not acknowledged does
not mark rows published.

**How do you detect consumer lag?**
Log-end offset minus committed offset, per group and partition. PayFlow computes it **broker-side**
(`KafkaLagMonitor`), because the client metric disappears exactly when the consumer dies. It alerts on lag that is
growing for 10 minutes, not on a momentary value.

**What causes hot Kafka partitions?**
- A skewed key (one key carries a large share of traffic, e.g. one merchant's id).
- Too few partitions.
- A custom partitioner.

PayFlow keys by paymentId (uniform), so partitions are even. A hot *account* does **not** create a hot partition
here, because keys are payments, not accounts. The contention moves to the database row instead.

**How do you tune Kafka?**
Measure first. Producer:
- `acks=all` + idempotence (non-negotiable for money)
- `linger.ms` / `batch.size` for batching (only matters if you send asynchronously, which is exactly the relay fix)
- compression for bandwidth

Consumer:
- `max.poll.records` bounds transaction work per poll
- concurrency ≤ partitions
- fetch sizes for throughput

Never trade durability (acks, ISR) for throughput on financial events.

**Partition scaling?**
At most one consumer per partition per group is active: 6 partitions means at most 6 parallel consumers; extras
idle (measured in BOTTLENECK-ANALYSIS). Increasing partitions:
- adds parallelism
- **remaps keys** (per-key ordering breaks across the change boundary)
- adds broker overhead

So choose the partition count up front from the target consumer parallelism.

## Database and JVM

**Why doesn't adding application replicas solve a hot account?**
Every payment from that account must lock the same `account_balance` row (`SELECT … FOR UPDATE`) to keep the
balance correct. The lock serializes those transactions regardless of how many pods run them. More replicas add
waiters, not throughput.

Throughput per hot account ≈ 1 / (lock hold time). To do better without weakening correctness:
- shorten the transaction
- batch reservations per account
- split the account into sub-balances (reserve from any shard with available funds)

**What causes hot database rows?**
A shared counter or aggregate every transaction touches: account balances, sequence-like rows, "last updated" rows.

**How do you tune HikariCP? Why can too many connections hurt?**
Size the pool to the work the database can do in parallel, not to the thread count. Rule of thumb
≈ cores × 2 on the DB side, divided across replicas.

Too many connections hurt because:
- context switching and lock contention inside PostgreSQL
- memory per backend
- `replicas × pool` can exceed `max_connections`: 20 pods × 30 = 600

Measured in PayFlow: at normal load the pool used 5/20 with ~8 ms hold time. The limit is transactions × hold
time, not pool size.

**Graceful shutdown?**

| Moment | What happens |
|---|---|
| On SIGTERM | readiness goes DOWN; Kubernetes preStop (10 s) lets endpoints drain |
| In-flight HTTP requests | finish (25 s phase) |
| Kafka listeners | stop after the current record; offsets committed only after processing, so an unfinished record is redelivered and deduplicated |
| Schedulers | the relay batch is allowed to finish (15 s), bounded by the 5 s send timeout |

terminationGracePeriodSeconds (45 s) covers the sum. Verified live in F-21.

**Liveness vs readiness?**
- Liveness: "is this process able to make progress?" Never check dependencies, or a DB outage restarts every pod
  (restart storm).
- Readiness: "should traffic come here?" PayFlow requires PostgreSQL. It does **not** require Kafka: the outbox
  absorbs a broker outage, and admission control protects the backlog.

## Operating it

**How would you debug high p99?**
1. Which p99: acceptance or completion?
2. Acceptance: look at DB acquire wait and connection hold time, GC pauses, CPU, lock waits (hot account), then
   slow traces (exemplars) and their span breakdown.
3. Completion: saga step p95 next to outbox age and consumer lag. Whichever rises first is the cause.

**Metrics vs logs vs traces?**
- Metrics: *what* and *how much* (cheap, aggregated, alertable).
- Traces: *where* the time went in one request across hops.
- Logs: *what happened* to this payment and why (full context, one line per boundary).

Join them by ids (traceId, correlationId), not timestamps.

**How do you trace an asynchronous payment?**
The outbox row stores the W3C `traceparent` captured in the originating transaction; the relay puts it on the
Kafka record; consumer observations continue it. The whole saga is one trace, and the relay gap is the publication
delay. With 10 % sampling, logs carry the traceId for every payment regardless.

**What happens if Kafka is unavailable? Should the payment API remain available?**
Yes, within limits:
- Payments and outbox rows commit in PostgreSQL (the durable queue), so acceptance stays available and fast.
- Completion pauses; saga recovery holds (no amplification).
- When the oldest unpublished event is 60 s old, admission control returns 503: we stop promising what we cannot
  complete in SLO.
- When Kafka returns, the relay drains in order.

Measured in F-01.

**How do you reconcile ledger and balance? Why not blindly repair?**
- A continuous job compares independently written records in one consistent snapshot (nine invariants, e.g.
  available + reserved = ledger balance).
- In-flight events get a grace period; two-run confirmation filters noise.
- It never repairs: which side is wrong *is* the question, auto-repair would erase the evidence of the bug that
  caused the drift, and "fixing" money by overwriting is how small incidents become audit findings.

Correction is a compensating, approved journal (ADR-021).

**What happens when a settlement outcome is unknown?**
1. Timeout ⇒ UNKNOWN, never a decline.
2. A same-key retry recovers the rail's recorded answer (the rail deduplicates).
3. If retries are exhausted: MANUAL_REVIEW with funds still reserved.
4. An operator sees our attempts plus a **live rail inquiry**, then RESUMEs (idempotent) or, only if the rail does
   not report ACCEPTED, voids the key at the rail and lets the normal decline path release funds.

**How do you handle manual review securely?**
- Dedicated scope `ops:manual-review` (not admin), checked at the route and in the use case.
- Per-operator rate limit.
- Idempotency-Key on every decision.
- Mandatory reason and ticket.
- Append-only audit (the DB role cannot update or delete) with frozen evidence.
- Optimistic locking so two operators cannot both decide.
- **No endpoint that sets an outcome.**

**What does production Zero Trust mean for Kafka?**
- Every client authenticates (SASL/SCRAM or mTLS).
- Every action is authorized by ACL, with deny-by-default.
- One identity per service, least privilege (single writer per topic; readers only of what they need: ledger
  cannot read fraud PII).
- Traffic is encrypted (TLS).
- Credentials rotate.

PayFlow implements authentication and ACLs with deny-by-default and verifies them. TLS, per-service identities in
the monolith, and rotation are documented gaps (ADR-023).

**How would you design PayFlow for 10× traffic?**
- Shard the relay by key hash (or CDC).
- Size partitions for the target consumers (e.g. 24).
- PgBouncer in front of PostgreSQL.
- Separate read replicas for status reads.
- Sub-balances for hot accounts.
- Scale the stateless path on CPU and lag (KEDA).
- Load-test each step against the SLOs.

**… for 100×?**
- Service extraction with per-service databases.
- CDC outbox.
- Account-sharded ledger.
- Multi-region active/passive with regional idempotency.
- Tail-sampled tracing.
- Cell-based deployment to bound the blast radius.

**Where does it break, why, how do we detect it, how do we recover, what trade-off did we choose, and what
evidence do we have?**
That is the WP-03 review in one line. See WP-03-ARCHITECTURE-REVIEW.md: the final Principal Engineer questions are
answered there with the measured numbers.
