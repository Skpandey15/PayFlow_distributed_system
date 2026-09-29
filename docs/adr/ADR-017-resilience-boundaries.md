# ADR-017: Resilience4j only at the synchronous remote boundary (settlement rails)

- Status: Accepted (WP-03)
- Date: 2026-09-27

## Context

WP-03 asks for timeouts, retries, circuit breakers, bulkheads and rate limiting. Applied everywhere, each of these
does damage:

| Where | What goes wrong |
|---|---|
| Repository methods | A breaker around PostgreSQL hides the authoritative store's state and turns a 3 s pool timeout into a different failure. Retries inside a transaction retry half-done work. |
| Kafka consumers | Kafka already has classified, bounded, non-blocking retries and a DLT (ADR-014). A second retry layer multiplies attempts and stalls partitions. |
| Domain and use cases | Infrastructure policy leaks into business code. |

PayFlow has exactly **one synchronous call to a system it does not operate**: submitting a settlement instruction
to a rail (card network, UPI switch, bank). In WP-03 that rail became a real HTTP dependency: a separate process,
the rail simulator, reached over the network.

## Decision

1. **Resilience4j is used only in `settlement.adapter.out.gateway.HttpSettlementRailGateway`**. It is wired
   programmatically in `SettlementRailConfiguration`, with no annotations and no AOP. ArchUnit enforces that only
   `adapter.out`, `infrastructure` and the edge (`platform.web`) may depend on Resilience4j. The edge uses it for
   rate limiting (ADR-017b below).
2. **Decorator order:** `Retry( CircuitBreaker( Bulkhead( HTTP call with connect + response timeout ) ) )`.
3. **The failure vocabulary of the port is explicit** (`SettlementGatewayPort`):

   | Outcome | Meaning | Examples |
   |---|---|---|
   | known outcome | answer received | ACCEPTED, DECLINED |
   | `NOT_SENT` | provably never reached the rail | connection refused, circuit open, bulkhead full, 429 |
   | `UNKNOWN` | may have been processed | response timeout, 5xx, connection lost |
   | permanent | the rail rejected the request as invalid | 4xx → `InstructionRejectedException`, then the DLT |

   **A timeout is never treated as a decline.** An UNKNOWN outcome is resolved by a same-key retry (the rail
   deduplicates), by the saga's recovery, or by an operator using the rail's status inquiry (ADR-022).
4. **Policy:**

   | Mechanism | Setting | Why |
   |---|---|---|
   | Timeouts | connect 500 ms, submit response 2 s, inquiry 1 s | See RESILIENCE-ARCHITECTURE.md |
   | Retry | 2 attempts total; 200 ms × 2ⁿ, ±50 % jitter | Only for connection refused and 503 |
   | Circuit breaker | per rail, count window 20, min 10 calls; opens at 50 % failures or 80 % slow calls (> 1.5 s); 15 s open; 3 half-open probes | |
   | Payment bulkhead | semaphore, 16 concurrent calls per rail, no waiting | |
   | Operator bulkhead | separate semaphore, 2 per rail | |

   Bulkhead rejections and open-circuit rejections are **not** recorded as rail failures.
5. **Log once:**
   - The adapter logs nothing per call. It puts `dependency`, `circuitBreakerState` and `deliveryOutcome` into
     the logging context, so the consumer boundary's single WARN line carries them.
   - Circuit state changes are logged once each (WARN on OPEN).
   - Everything is also counted: `payflow.settlement.rail.calls{rail,operation,outcome}` and the Resilience4j meters.

## Alternatives

| Alternative | Why not |
|---|---|
| `@CircuitBreaker` / `@Retry` annotations (resilience4j-spring-boot) | Hidden policy, proxy pitfalls (self-invocation), and the Boot-4 starter would couple to Spring internals. The explicit decorators are ~20 lines. |
| Retry everything, including timeouts | Retry amplification against a slow provider (see below) |
| Thread-pool bulkhead | Adds a hand-off and a queue. Callers are already dedicated consumer threads or virtual threads, so a semaphore bounds concurrency without queuing. |
| TimeLimiter | Needs futures. The JDK HTTP client already enforces the response timeout on a blocking call. |
| Service mesh for retries and timeouts | Not present in the lab. It cannot distinguish NOT_SENT from UNKNOWN, which is a financial-correctness distinction. |

## Trade-offs

**Retry amplification is bounded but real.** Per settlement command, the worst case is:

| Layer | Attempts |
|---|---|
| Resilience4j | 2 |
| Kafka delivery attempts | × 4 |
| Saga re-issues (initial + 3) | × 4 |
| **Total** | **32 calls** |

These are spread over about 8 minutes. An open circuit converts most of them into zero network calls, so what
the provider sees is dominated by half-open probes.

A second trade-off: an open circuit on one rail sends that rail's settlements through the retry topics to the
DLT. They are then re-issued by saga recovery after 2 minutes, or reach manual review. That is intended:
money is never moved on a guess.

## Operational consequences

- Alerts: `SettlementCircuitOpen` (ticket after 2 min), `SettlementCircuitOpenProlonged` (page after 15 min).
- Dashboard "Resilience" shows circuit state, call outcomes, bulkhead permits and retry outcomes.
- The breaker and bulkhead settings are configuration (`payflow.settlement.rail.*`). Changing them needs a load
  or failure test (see TUNING-RESULTS.md).

## Failure implications

| Failure | Behaviour |
|---|---|
| Rail down | Fast NOT_SENT failures. Kafka retries, then the DLT, then recovery re-issues or escalates. Money never moves twice (idempotency key = paymentId). |
| Rail slow | Slow calls open the circuit before calls start timing out. Payment acceptance is unaffected (the HTTP path never calls the rail). |
| Rail processed it but the answer was lost | UNKNOWN. A same-key retry returns the recorded outcome. If retries are exhausted: MANUAL_REVIEW with the rail inquiry as evidence. |

## ADR-017b: edge rate limiting and admission control (same boundary rule)

- **Per-subject token bucket** (Resilience4j `RateLimiter`, no waiting):
  - `POST /api/v1/payments`: 20/s per authenticated subject.
  - State-changing `/api/v1/ops/**`: 10/min per operator.
  - Response: 429 + Retry-After.
  - Limits are per instance. A shared limit belongs in the API gateway.
- **Admission control:** while the oldest unpublished outbox event is older than 60 s, `POST /api/v1/payments`
  answers 503 + Retry-After 30. A short Kafka outage is still absorbed by the outbox (WP-02 degraded mode). A
  sustained backlog stops growing instead of silently breaking the completion SLO for every new payment.
