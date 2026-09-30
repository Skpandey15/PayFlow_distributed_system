# Resilience architecture (WP-03)

Principle: **every mechanism exists because of an identified failure mode**, and each one lives at exactly one
boundary. Measured evidence is in `docs/performance` and `docs/failures/WP-03-FAILURE-MATRIX.md`.

## 1. Map of boundaries and mechanisms

| Boundary | Failure modes | Mechanism (and where) | Deliberately not |
|---|---|---|---|
| HTTP edge (inbound) | one caller floods; intake beyond what the async pipeline can finish | per-subject rate limit (429); outbox-age admission control (503) (`platform.web.traffic`) | global request queue; client IP limits (callers are authenticated) |
| Synchronous call to settlement rails (outbound, external) | slow, down, throttling, lost responses | timeout, bounded retry with backoff and jitter, circuit breaker per rail, semaphore bulkheads (`HttpSettlementRailGateway`) | fallback "assume declined" (financially wrong) |
| Kafka consumers (async) | transient infrastructure, poison messages | WP-02 classified non-blocking retry topics + DLT (ADR-014) | Resilience4j (would multiply retries) |
| Outbox → Kafka (async) | broker down or slow; relay slower than production | outbox absorbs outages; pipelined relay (ADR-019); admission control stops the backlog growing without bound | dropping or skipping events |
| Saga (workflow) | participant never answers | step timeouts, bounded re-issue, escalate unknown outcomes; **recovery holds while commands are unpublished** (WP-03) | auto-compensating unknown outcomes |
| PostgreSQL (authoritative) | outage, pool exhaustion, lock contention | pool timeout 3 s → 503; transaction timeout 5 s; readiness DOWN; deterministic lock order (WP-02) | circuit breaker or retry around repositories |
| MongoDB (fraud store) | outage | 2 s server selection, 3 s read timeout → transient failure → retry topics → saga recovery (fail closed) | fallback "approve without scoring" |

## 2. Settlement rail policy, and the derivation of each number

### Timeouts

| Timeout | Value | Why |
|---|---|---|
| Connect | **500 ms** | A healthy connect inside a region takes < 10 ms. 500 ms tolerates SYN retransmits under packet loss but fails fast when the rail is gone. |
| Submit response | **2 s** | Rails answer in ~100–300 ms normally (simulator: < 10 ms). 2 s is well above the healthy p99 and well below every caller deadline, see the chain below. |
| Inquiry / void response | **1 s** | An operator is waiting; these calls are cheap lookups. |

**Deadline chain** (each timeout must be smaller than its caller's):

```
rail response timeout      2 s
× attempts (2) + backoff   ≈ 4.4 s  worst case per consumer delivery
< consumer processing budget        (max.poll.interval.ms = 300 s: never at risk)
< Kafka retry topic delays          1 s, 3 s, 9 s between deliveries
< saga settlement step timeout      120 s (recovery re-issues after this)
< rail idempotency window           24 h (manual-review RESUME is refused after it)
```

**Timeout ≠ failure.** A response timeout returns `DeliveryOutcome.UNKNOWN`. The settlement stays PENDING, the
attempt is recorded (`submission_attempts`, `last_attempt_outcome`, `last_error_code`), and the next delivery
retries **with the same idempotency key**, so the rail returns its recorded answer. Only a known decline declines.

### Retry

- **Maximum 2 attempts** (1 retry), exponential backoff 200 ms × 2ⁿ with **±50 % jitter**.
- **Retried:** connection refused (NOT_SENT) and 503 (UNKNOWN; safe because the key deduplicates).
- **Not retried here:**

  | Failure | Why not |
  |---|---|
  | Timeouts | Retrying a slow provider amplifies its overload. The Kafka layer retries seconds later instead. |
  | 429 | The provider asked us to slow down |
  | Open circuit, full bulkhead | Local fast-fail |
  | 4xx | Permanent: DLT |
  | Declines | Business outcome |

- **Why jitter:** many consumer threads fail at the same instant when the rail blips. Without jitter they retry in
  lockstep and hit the recovering rail together.

**Retry amplification (worst case per settlement):**

| Layer | Attempts |
|---|---|
| Resilience4j | 2 |
| Kafka deliveries | × 4 |
| Saga (initial + 3 re-issues) | × 4 |
| **Total** | **32 calls** |

Example: 100 settlements during an outage could produce 3,200 calls, but:
- the circuit opens after 5 of the first 10 recorded failures, so most attempts become zero-cost CIRCUIT_OPEN failures;
- the half-open state admits 3 probes every 15 s per rail;
- recovery re-issues only after the 120 s step timeout, and holds while the outbox is behind.

Measured in the failure campaign (F-11 retry storm).

### Circuit breaker (per rail)

| Setting | Value | Why |
|---|---|---|
| Window | count-based, 20 calls | Settlement volume per rail is steady; a count window reacts in "calls", independent of traffic. |
| Minimum calls | 10 | Never open on 1 of 2 failures. |
| Failure-rate threshold | 50 % | A rail failing half of its calls is effectively down; below that, Kafka retries cope. |
| Slow-call threshold | 1.5 s (< 2 s timeout), at 80 % | Opens on **degradation before** calls start timing out (UNKNOWN outcomes are the expensive kind). |
| Open duration | 15 s | Short enough that a blip costs little, long enough to shed load from a struggling rail. |
| Half-open probes | 3 | One success is luck; three is a signal. |
| Recorded | NOT_SENT/UNKNOWN failures from the rail, including 429 | |
| **Not recorded** | bulkhead-full, circuit-open, 4xx instruction rejections | Not the rail's health |

**Per rail:** a UPI outage must not stop card settlements. Verified by `SettlementRailResilienceTest`.

### Bulkheads (semaphore, fail fast)

- **Payment traffic: 16 concurrent calls per rail.**
  - This is the provider's contracted concurrency, in the lab's assumption.
  - Consumer concurrency is 3 per container, but retry-topic containers (3 per group) also call the rail, so
    up to 12 concurrent callers per instance, and 24 or more with two instances. The bulkhead makes the ceiling
    explicit and independent of Spring container counts.
- **Operator traffic: 2 per rail, separate.**
  - Inquiries and voids come from HTTP virtual threads, which are effectively unbounded.
  - A separate compartment means an investigation burst cannot take payment capacity, and saturated payment
    traffic cannot block the operator who is trying to resolve the incident.
- **Semaphore, not thread pool:** callers are already dedicated consumer threads or virtual threads. A thread-pool
  bulkhead would add a hand-off and a queue: latency, and a second place where work piles up.
- **Blast radius without a bulkhead:** limited already, because settlement runs on its own listener threads,
  never on HTTP request threads, and never holds a DB connection across the call ("record intent, call, record
  outcome"). The bulkhead's job is protecting the **provider** and bounding in-flight **unknown outcomes**.
  Measured: API p99 during rail slowness (F-07, F-12).

## 3. Edge: rate limiting and admission control

| Policy | Who | What | Rate | On breach | Reason |
|---|---|---|---|---|---|
| payments | each authenticated subject | `POST /api/v1/payments` | 20/s, no queueing | 429 + `Retry-After: 1` | A human never approaches it; a looping script or stolen token does. |
| ops | each operator | `POST /api/v1/ops/**` (DLT replay, manual-review decisions, reconciliation runs) | 10/min | 429 | Stops scripted mass actions on money-moving operations. |
| admission | all callers | `POST /api/v1/payments` | while oldest unpublished outbox event > 60 s | 503 + `Retry-After: 30` | Beyond 60 s every new payment misses the completion SLO; accepting more only deepens the backlog. |
| work-in-progress window | all callers | `POST /api/v1/payments` | payments in PayFlow's own pipeline ≤ 300; every 250 ms, half the free room is granted as credits | 503 `PAYMENTS_BUSY` + `Retry-After: 2` | Little's law: bounding work in progress bounds completion time. A window cannot oscillate like a threshold gate (TUNING-RESULTS §11). |

Limits are per instance (Resilience4j is in-process): with N replicas a subject can get N × 20/s. A cluster-wide
limit belongs in the API gateway. Idle buckets are evicted after 10 minutes, so memory is bounded by active
subjects.

## 4. Backpressure (producer faster than consumer)

| Hop | Buffer | Bound | Signal | Action |
|---|---|---|---|---|
| HTTP → PostgreSQL | none (synchronous) | Hikari pool 20, acquisition timeout 3 s | `hikaricp_connections_pending` | 503 (DependencyUnavailable); readiness stays UP |
| Commit → Kafka | outbox table (durable, on disk) | admission control at 60 s oldest age | `payflow.outbox.oldest.age.seconds` | 503 on new payments; relay drains |
| Kafka → consumers | the topic log (durable, retention 7 d) | partitions (6) × consumer concurrency | broker-side lag monitor | consumers pull at their own rate; nothing in memory grows |
| Saga re-issue | outbox | recovery holds while the oldest unpublished command > 15 s | `payflow.saga.recovery.held` | no amplification of a backlog |

There is **no unbounded in-memory queue** anywhere:
- Kafka consumption is pull-based.
- `max.poll.records=50` bounds each fetch.
- The producer buffer is bounded (`buffer.memory` default 32 MiB, `max.block.ms` 5 s).
- Every backlog lives on disk in PostgreSQL or Kafka, where it is measured.

## 5. Degraded modes (explicit)

| Dependency down | Accept payments? | Behaviour | Recovery |
|---|---|---|---|
| Kafka | **Yes**, until the oldest unpublished event is 60 s old, then 503 | Payment and outbox rows commit; saga recovery holds (no amplification) | Relay drains in id order; sagas continue |
| Outbox backlog > 60 s (any cause) | No (503, Retry-After 30) | Intake paused, processing continues | Reopens automatically when the age falls |
| MongoDB (fraud) | Yes (accepted as CREATED) | Risk step fails closed: retries, then the saga times out and REJECTS (`RISK_ASSESSMENT_TIMEOUT`); never approves unscored | Automatic |
| Settlement rail | Yes | Circuit opens; settlements retry, then DLT, then recovery re-issue, then MANUAL_REVIEW; funds stay reserved | Automatic on rail recovery, or an operator via manual review |
| Settlement outcome unknown | Yes | MANUAL_REVIEW, never auto-compensated | Operator: RESUME or CONFIRM_NOT_SETTLED with rail evidence |
| PostgreSQL | **No** | 503 fast (3 s pool timeout); readiness DOWN; liveness UP (no restart storm) | Automatic when the DB returns; nothing half-written (transactions) |
| Keycloak (IdP) | Existing tokens keep working until expiry (JWKS cached); no new logins | Scrape auth fails → `PayFlowTargetDown` | Automatic |

## 6. Exception taxonomy additions (no second model)

WP-03 reuses the WP-02 taxonomy. The rail adapter raises only the port's exceptions:

| Exception | Code(s) | Category (FailureClassifier) | Retried by | Opens breaker |
|---|---|---|---|---|
| `GatewayUnavailableException(NOT_SENT)` | `SETTLEMENT_RAIL_UNREACHABLE` | TRANSIENT_INFRASTRUCTURE | Resilience4j (once) + Kafka | yes |
| | `SETTLEMENT_RAIL_THROTTLED` | TRANSIENT_INFRASTRUCTURE | Kafka only | yes |
| | `SETTLEMENT_RAIL_CIRCUIT_OPEN`, `SETTLEMENT_RAIL_BULKHEAD_FULL` | TRANSIENT_INFRASTRUCTURE | Kafka only | **no** |
| `GatewayUnavailableException(UNKNOWN)` | `SETTLEMENT_RAIL_UNAVAILABLE` (503) | TRANSIENT_INFRASTRUCTURE | Resilience4j (once) + Kafka | yes |
| | `SETTLEMENT_RAIL_TIMEOUT`, `SETTLEMENT_RAIL_ERROR`, `SETTLEMENT_RAIL_CONNECTION_LOST` | TRANSIENT_INFRASTRUCTURE | Kafka only | yes |
| `InstructionRejectedException` | `SETTLEMENT_RAIL_REJECTED_INSTRUCTION` | BUSINESS_RULE (permanent) | no | no |

Edge rejections (429 / 503) are not exceptions: the interceptor answers directly and counts
`payflow.traffic.rejected{policy}`. HTTP 503 from admission is an availability SLI error by definition
(SLI-SLO.md); 429 is not (the caller exceeded its quota).

## 7. Log-once ownership (unchanged rule, new participants)

| Boundary | Logs |
|---|---|
| HTTP (ApiExceptionHandler) | synchronous request failures, once |
| Kafka (EventConsumerSupport) | asynchronous processing failures, once, now including dependency, circuit state and delivery outcome from the rail adapter |
| Saga (PaymentSagaListener, SagaRecoveryJob) | transitions, compensations, recovery actions, hold/resume transitions |
| Rail adapter | circuit state **changes** only (one line each), never per call |
| Edge traffic control | admission open/close transitions only, never per rejected request |
| Ops (`payflow.ops.audit`) | one line per manual-review decision |
