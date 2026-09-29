# Pattern Catalog (only patterns actually implemented)

Each entry is written as Problem → Pattern → Where → Why → Alternative → Trade-off.

## Tactical DDD and enterprise patterns

### Aggregate / Aggregate Root
- **Problem:** Payment state must change consistently: valid transitions, a reason on failure, and events emitted.
- **Where:** `Payment`, `Account`, `JournalEntry`, `Settlement`.
- **Why:** Invariants are enforced in one place, and transitions are methods, not setters.
- **Alternative:** An anemic entity plus a service with `if` statements, which spreads the rules and lets them be bypassed.
- **Trade-off:** Snapshot and rehydrate plumbing for persistence.

### Value Object
- **Where:** `Money`, `AccountId`, `PaymentId`, `LedgerLine`, `RiskSignal`, `ChannelContext`.
- **Why:** Validity at construction, immutability, and equality by value. `Money` cannot exist with a wrong scale.
- **Alternative:** Raw `BigDecimal` plus `String` currency everywhere. Precision bugs follow.
- **Trade-off:** Mapping at the boundaries.

### Repository
- **Where:** `PaymentRepositoryPort`, `AccountRepositoryPort`, `JournalEntryRepositoryPort` and `SettlementRepositoryPort` (ports), plus their JPA adapters.
- **Why:** Use cases see a collection of aggregates. JPA stays behind the port.
- **Alternative:** Injecting a Spring Data `JpaRepository` into services, which leaks entities and persistence semantics.
- **Trade-off:** Adapter code and mappers.

### Domain Service
- **Where:** `RiskScoringPolicy`, which combines rules into a decision.
- **Why:** The logic belongs to no single entity.

### Application Service
- **Where:** `CreatePaymentService`, `CancelPaymentService`, `PaymentSagaService`, `FundsService` and the others.
- **Why:** They orchestrate ports and own transaction boundaries. They contain no business rules.

### Domain Event
- **Where:** the sealed `PaymentEvent` hierarchy, pulled after each transition and passed to `PaymentEventPublisherPort` in-transaction.
- **Why:** Decouples what happened from who reacts. The in-transaction call is the seam for the WP-02 Outbox.
- **Alternative:** Direct calls to other contexts, which causes temporal coupling.
- **Trade-off:** The WP-01 publisher only logs (not durable, finding m12).

### Anti-Corruption Layer
- **Where:** `payment.adapter.out.{account,fraud,settlement,ledger}`.
- **Why:** Payment speaks its own language (`PaymentParty`, `RiskVerdict`, `SettlementOutcome`). Other contexts' models never leak in, and the ACL is the swap point when a context becomes remote.
- **Trade-off:** Some duplicated enums and records, on purpose.

### Shared Kernel
- **Where:** `shared.domain` (`Money`, `AccountId`, `Identifiers`, domain exceptions) and `shared.application` (`Actor`, `TransactionRunner`, exception categories).
- **Why:** Money semantics must be identical across contexts.
- **Trade-off:** A change to it ripples into every context, so it is kept deliberately tiny.

## GoF patterns

### Strategy
- **Where:** `RiskRule` implementations (HighAmount, Velocity, MissingDevice, HighRiskCountry); `SettlementGatewayPort` per rail, selected by `SettlementGatewayRouter`.
- **Why:** Rules and rails vary independently. Adding one means adding a class, which is Open/Closed.
- **Alternative:** A `switch(method)` with rail logic inline, which grows without bound and is hard to test.
- **Trade-off:** Indirection. The router fails startup if a rail has zero or two gateways.

### Adapter
- **Where:** every `adapter.out` class, for example `JpaPaymentRepositoryAdapter` and `MongoFraudAssessmentAdapter`, and the simulated rails behind `SettlementGatewayPort`.
- **Why:** Translates a framework or provider API into our port.
- **Trade-off:** More types.

### Template Method (removed in WP-03)
- WP-01/02 used it for the in-process `SimulatedRailGateway` family. WP-03 replaced the simulated rails with a real HTTP dependency (the rail simulator) and one `HttpSettlementRailGateway` per rail, so the pattern and its classes are gone. Provider idempotency is now honoured by the external rail itself (as real providers do) and verified over HTTP.

### State (table-driven)
- **Where:** `PaymentStatus` with an `EnumMap` of allowed transitions.
- **Why:** The behaviour that differs per state is only which transitions are legal.
- **Alternative:** Full GoF State with one class per state. It is justified only when behaviour per state differs substantially, and here it does not.
- **Trade-off:** If per-state behaviour grows (for example refunds with partial captures), migrate to State classes.

### Factory Method
- **Where:** `Payment.initiate`, `Account.open`, `JournalEntry.post`, `Settlement.initiate`, `Money.of`.
- **Why:** Named creation that enforces creation-only invariants and emits creation events. It is distinct from `rehydrate`, which does not re-emit events.

### Facade
- **Where:** inbound use-case ports seen by controllers, for example `CreatePaymentUseCase`.
- **Why:** Controllers see one intention-revealing method, not the orchestration of five ports.

### Memento-like snapshot
- **Where:** `PaymentSnapshot`, `AccountSnapshot`, `SettlementSnapshot`.
- **Why:** Externalises full state for persistence without public setters or JPA in the domain.

## Distributed-system patterns (WP-02)

### Transactional Outbox
- **Problem:** state change and event publication cannot be atomic across PostgreSQL and Kafka (the dual write).
- **Where:** `OutboxWriter` plus `<ctx>.outbox_event`, used by `OutboxPaymentEventPublisher`, `OutboxSagaCommandPublisher`, `OutboxFundsEventPublisher` and `OutboxSettlementEventPublisher`.
- **Why:** one local transaction covers both.
- **Alternative:** publish-after-commit (loss), XA (rejected), CDC (ADR-010).
- **Trade-off:** at-least-once, about 200 ms latency, purge duty.
- **Failure behaviour:** crash after commit means publish later; crash after the ack means a duplicate the consumers absorb.

### Polling Publisher (message relay)
- **Where:** `OutboxRelay` with an advisory-lock single writer and id order.
  - WP-03 made it **pipelined**: a batch is handed to the idempotent producer, acknowledgements are awaited once,
    and only acknowledged rows are marked.
  - WP-03 also made it **draining**: full batches go back to back within a 1 s budget.
- **Alternative:** Debezium CDC, or sharded relays (ADR-019).
- **Trade-off:** single writer per outbox.
- **Evidence:** 106 → 5,552 events/s on the same build (relay benchmark); K2 closed.

### Idempotent Consumer (Inbox)
- **Problem:** at-least-once delivery.
- **Where:** `IdempotentExecutor` plus `<ctx>.processed_event`; natural keys in Settlement and Fraud.
- **Alternative:** Redis dedup (a second store, lossy).
- **Failure behaviour:** redelivery gives DUPLICATE with no effect.

### Saga (orchestration) and Compensating Transaction
- **Where:** `PaymentSaga`, `PaymentSagaService`; the compensation is `ReleaseFunds`.
- **Alternative:** choreography (kept for the Ledger follower), 2PC.
- **Trade-off:** no isolation; intermediate states are visible.
- **Failure behaviour:** stale replies are ignored; unknown outcomes are escalated.

### Process Manager recovery / Scheduler-Agent-Supervisor
- **Where:** `SagaRecoveryService` plus `SagaRecoveryJob`, using SKIP LOCKED.
- **Why:** lost commands (DLT) and stuck steps self-heal.
- **Trade-off:** timeouts must exceed normal latency.

### Publish-Subscribe and Competing Consumers
- **Where:** consumer groups (for example `funds.events` is read by `payment-service` and `ledger-service` independently); up to 6 competing instances per group.

### Retry and Dead Letter Channel
- **Where:** `@RetryableTopic` per group, `FailureClassifier`, `DeadLetterObserver`, `DeadLetterReplayService`.
- **Trade-off:** non-blocking retries can reorder a key; handlers are order-tolerant.

### Poison Message handling
- **Where:** `EventDeserializationException`, `UnsupportedEventVersionException` and contract violations go straight to the DLT; the partition is never blocked.

### Correlation Identifier
- **Where:** envelope `correlationId`, `causationId`, `sagaId`, plus the W3C `traceparent`, via `MessageContext` and `TraceparentSupplier`.

### Event-Carried State Transfer
- **Where:** commands carry the amount, parties and method, so participants never call back into Payment.
- **Trade-off:** larger messages, and data is duplicated per event.

### Event Notification
- **Where:** `payment.events` (a public lifecycle stream).

### Envelope Wrapper and Canonical Data Model (published language)
- **Where:** `EventEnvelope` plus `com.payflow.contracts`, with JSON Schemas checked in the build.

### Upcaster (schema evolution)
- **Where:** `EventCatalog` upcasters (`RiskAssessedV2.fromV1`).

### Pessimistic Offline Lock (row lock)
- **Where:** `account_balance` `SELECT … FOR UPDATE` for reservations, with deterministic lock ordering in capture.
- **Alternative:** optimistic locking (retry storms on hot accounts).

### Tombstone (out-of-order compensation)
- **Where:** a RELEASED reservation created by a release that overtakes its reserve.

## Resilience and operations patterns (WP-03)

Every entry below exists because of a measured or identified failure mode (RESILIENCE-ARCHITECTURE.md).

### Timeout
- **Problem:** a remote call without a bound ties up the caller indefinitely; a slow rail would stall settlement consumers.
- **Where:** JDK HttpClient connect timeout (500 ms) and per-request response timeout (2 s submit, 1 s inquiry) in `SettlementRailConfiguration`.
- **Alternative:** Resilience4j TimeLimiter (needs futures; unnecessary on a blocking client with native timeouts).
- **Trade-off:** a timeout creates an UNKNOWN outcome. It is modelled explicitly (`DeliveryOutcome.UNKNOWN`), never treated as a decline.
- **Failure behaviour and evidence:** `SettlementRailResilienceTest.timeoutIsAnUnknownOutcome…` (the rail had accepted; the inquiry proves it).

### Retry with exponential backoff and jitter
- **Problem:** brief connection failures would otherwise go through the slower Kafka retry path.
- **Where:** Resilience4j `Retry` in `HttpSettlementRailGateway`: 2 attempts, 200 ms × 2ⁿ, ±50 % jitter, only for connection refused and 503.
- **Alternative:** rely on Kafka retry topics alone (slower); retry everything (amplification).
- **Trade-off:** bounded amplification, 32 calls per payment in the worst case, collapsed by the breaker.
- **Evidence:** `…unavailableIsRetriedOnceWithTheSameKey` (exactly 2 calls).

### Circuit Breaker (per dependency instance)
- **Problem:** hammering a failing or degrading rail wastes resources and delays its recovery.
- **Where:** one Resilience4j `CircuitBreaker` per rail. Opens on 50 % failures or 80 % slow calls; bulkhead and circuit rejections are not counted against the rail.
- **Alternative:** a service-mesh outlier ejection (cannot tell NOT_SENT from UNKNOWN).
- **Trade-off:** during OPEN, settlements for that rail fail fast into retry, then DLT, then recovery or manual review.
- **Evidence:** `…circuitOpensOnFailuresFailsFastThenRecoversThroughHalfOpen`, `…slowRailOpensTheCircuit…`; failure campaign F-09/F-10.

### Bulkhead (semaphore)
- **Problem:** unbounded concurrency towards a provider with a contract limit; operator investigations competing with payment traffic.
- **Where:** two semaphores per rail: payment traffic (16) and inquiry/void (2); fail fast.
- **Alternative:** thread-pool bulkhead (a hand-off and a queue; pointless for dedicated or virtual threads).
- **Trade-off:** rejections become NOT_SENT transient failures (retried later).
- **Evidence:** `…bulkheadCapsConcurrentCallsToTheRailAndFailsFastAsNotSent` (max in flight ≤ 4 with limit 4); F-12.

### Rate Limiter (token bucket per subject)
- **Problem:** one caller (a script or a stolen token) can consume shared intake capacity.
- **Where:** `TrafficControlInterceptor` with a Resilience4j `RateLimiter` per authenticated subject (payments 20/s, ops 10/min); 429 + Retry-After.
- **Alternative:** a gateway-level global limiter (right for cluster-wide quotas, which PayFlow has no gateway for yet).
- **Trade-off:** per-instance limits (N replicas give N × the limit).
- **Evidence:** `TrafficControlIT`.

### Backpressure / Load shedding (admission control)
- **Problem:** accepting payments the asynchronous pipeline cannot finish in SLO grows an unbounded backlog.
- **Where:** admission 503 while the oldest unpublished outbox event is older than 60 s; saga recovery **holds** while commands are unpublished.
- **Alternative:** accept everything (baseline behaviour: hours of backlog); reject based on CPU (the wrong signal: acceptance was never the bottleneck).
- **Trade-off:** 503 counts against the availability SLO. It is deliberately visible.
- **Evidence:** `TrafficControlIT` (Kafka really paused); F-01/F-16; `PaymentSagaServiceTest.recoveryHolds…`.

### Health Check (liveness vs readiness)
- **Problem:** restart storms on dependency outages; traffic routed to an instance that cannot serve.
- **Where:** liveness (process only) and readiness (PostgreSQL, not Kafka, not MongoDB) via Actuator probe groups; Kubernetes startup, liveness and readiness probes.
- **Evidence:** F-03 (PostgreSQL down: readiness DOWN, liveness UP, no restart).

### Reconciliation (detect-only)
- **Problem:** independently written financial records can drift, and nothing watched them.
- **Where:** `reconciliation` context: nine invariants, one REPEATABLE READ snapshot, confirmation across runs, no writes to financial data.
- **Alternative:** auto-repair (rejected: destroys evidence and can make money wrong).
- **Evidence:** `ReconciliationIT`; F-19.

### Adapter / Anti-Corruption Layer (new instances)
- `SettlementContextAdapter` (payment → settlement operations) and `HttpSettlementRailGateway` (HTTP rail protocol → port vocabulary: NOT_SENT / UNKNOWN / known outcome).

### Decorator (explicit, not proxy-based)
- **Where:** `Retry.decorateSupplier(CircuitBreaker.decorateSupplier(Bulkhead.decorateSupplier(call)))`: functional decorators composed in one visible place.
- **Alternative:** annotation-driven AOP proxies (hidden ordering, self-invocation pitfalls). Rejected.

## Considered and NOT used in WP-03

| Pattern | Why not |
|---|---|
| Fallback ("assume declined", "approve without scoring") | Financially wrong. The only safe fallback for money is "hold and resolve with evidence". |
| Thread-pool bulkhead | See Bulkhead |
| Circuit breaker around PostgreSQL / Kafka consumers | Wrong layer (ADR-017) |
| CDC (Debezium) / sharded relay | Not needed at the measured ceiling (ADR-019 decision rule) |
| Sub-account (sharded balance) for hot accounts | Analysed, not implemented (BOTTLENECK-ANALYSIS §3) |

## Considered and NOT used in WP-02

| Pattern | Why not |
|---|---|
| CQRS / materialized views | No cross-context query need yet; `payment.events` enables them later |
| Event Sourcing | The relational financial core is authoritative; the outbox gives the needed events without the paradigm shift |
| Distributed lock (Redis) | Financial correctness belongs in PostgreSQL row locks and constraints |
| Kafka transactions (EOS) | Our effects are outside Kafka; they would not provide end-to-end exactly-once |

## Considered and deliberately NOT used (WP-01)

| Pattern | Why not (yet) |
|---|---|
| Builder | Records with named factories are enough; there are no objects with many optional parts |
| Chain of Responsibility | Risk rules are independent and additive, not "first handler wins". A plain list is clearer |
| Decorator / Proxy for resilience | Delivered in WP-03 as explicit functional decorators in the rail adapter (see above) |
| Command | Commands exist as data (`*Command` records), but there is no command bus or undo; one would be premature |
| CQRS | One store per context serves both reads and writes. Cross-context read models come with events (WP-02) |
| Saga / Outbox | Delivered in WP-02 (see the section above) |
| Unit of Work (explicit) | JPA's persistence context plus `TransactionRunner` already provide it |
