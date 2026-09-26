# WP-01 Principal Engineer Interview Defense

Short answers, each anchored in PayFlow's actual code and evidence.

## Architecture

**Why Clean Architecture?**
Payment rules (state machine, money precision, idempotency, ownership) change for business reasons, while Kafka, Resilience4j and Kubernetes change for technical reasons. Clean Architecture keeps these axes of change apart. The domain and use cases have zero framework imports (enforced by ArchUnit), so WP-02 and WP-03 arrive as new adapters, not as rewrites. The application tests run with fakes in milliseconds.

**Clean vs Hexagonal Architecture?**
The same core idea with different vocabulary:
- Hexagonal (ports and adapters) talks about the boundary: driving and driven sides.
- Clean and Onion talk about concentric layers with dependencies pointing inward.

PayFlow uses port and adapter packages (`port.in`, `port.out`, `adapter.in`, `adapter.out`) and the Clean dependency rule.

**What are Ports and Adapters?**
- A port is an interface owned by the application: `CreatePaymentUseCase` (inbound) or `PaymentRepositoryPort` (outbound).
- An adapter implements or calls a port with a technology: `PaymentController` (HTTP in), `JpaPaymentRepositoryAdapter` (SQL out), `FraudContextAdapter` (another bounded context).

**Why Dependency Inversion?**
So the high-level policy (`ProcessPaymentService`) does not depend on low-level detail (JPA, settlement rails). Both depend on the abstraction (`SettlementProviderPort`). The implementation can be in-process today, and a remote client with a circuit breaker tomorrow, with the use case unchanged.

**Why not the normal Controller → Service → Repository?**
In that style:
- Services depend on JPA entities and repositories.
- Transactions are implicit (`@Transactional` proxies, with the self-invocation trap).
- Business rules drift into services and controllers.

It is fine for CRUD. For money flows we want explicit transaction boundaries (`TransactionRunner`) and a domain that protects its own invariants.

**When does Clean Architecture become over-engineering?**
When there are no invariants and one adapter, meaning plain CRUD. PayFlow's Account context is close to that, and the review says so (O1). If the mappers outnumber the rules, simplify.

## DDD

**Why DDD?**
Because Payment, Fraud, Ledger and Settlement use the same words for different things ("authorized", "declined", "reference"), and each has different invariants. Bounded contexts give each its own model. Aggregates put invariants next to the data.

**Bounded Context vs Microservice?**
- A bounded context is a *model and language* boundary.
- A microservice is a *deployment* boundary.

PayFlow has 5 contexts in 1 deployable (ADR-001), because splitting before WP-02's Outbox would create synchronous coupling and dual writes. The boundaries are enforced (no cross-context tables; calls only via published ports through an ACL), so extraction is adapter work.

**Aggregate vs Entity?**
- An entity has identity and a lifecycle.
- An aggregate is a cluster of objects treated as one consistency boundary, accessed only through its root.

`JournalEntry` is an aggregate: its lines are only valid together, since debits must equal credits. `Payment` is an aggregate root with no children.

**Entity vs Value Object?**
- Entities are equal by identity (`Payment` with `PaymentId`).
- Value objects are immutable and equal by value (`Money`, `LedgerLine`). `Money("10.5","USD")` equals `Money("10.50","USD")`.

## Data

**Why PostgreSQL?**
For money we need:
- ACID multi-row writes (a payment together with its idempotency key)
- uniqueness under concurrency
- CHECK constraints as a last line of defence
- exact NUMERIC arithmetic

Tests prove the database rejects invalid rows even when the domain is bypassed.

**Why MongoDB?**
Fraud assessments are write-once, document-shaped evidence with an evolving schema (signals, device data, a model version), are never joined with the payment core, and can be extracted. I would also accept JSONB here; the choice is defensible, not inevitable (ADR-003).

**Why not MongoDB for the financial ledger?**
It lacks declarative integrity (CHECK constraints, foreign keys, a deferred balance check), and transactions need a replica set. Our ledger relies on a deferred constraint trigger (balanced at COMMIT) and append-only triggers plus privileges. In a document store those guarantees would live only in application code.

**Why polyglot persistence?**
Different workloads have different shapes. The cost: two technologies to operate, and no transactions across stores, so cross-store flows must be idempotent and resumable.

**Why database-per-service (context)?**
Ownership: a context can change its schema, scale or move without coordinating with others. Integration happens through APIs or events, which are contracts, not through tables, which are implementation details.

**Why not a shared database?**
Shared tables are a hidden integration contract. Every schema change becomes a cross-team negotiation, locks and load from one team hurt another, and the services can never be separated.

## Money

**Why BigDecimal?**
Binary floating point cannot represent 0.1. `new BigDecimal(0.1)` is 0.1000000000000000055…. PayFlow bans `double`/`float` fields and `BigDecimal(double)` construction through ArchUnit.

**How is currency represented?**
`java.util.Currency` (ISO 4217) inside `Money`, normalised to the currency's minor units (USD 2, JPY 0, KWD 3):
- Excess precision is rejected (422), never silently rounded.
- Cross-currency arithmetic throws.
- In storage: NUMERIC(19,4) plus a currency column in PostgreSQL, and Decimal128 in MongoDB.
- In the API: amounts are decimal *strings*.

## Idempotency and concurrency

**What prevents duplicate payments?**
The `Idempotency-Key` stored in `payment.idempotency_record` with primary key `(client_id, key)`, inserted in the **same transaction** as the payment. Proven by 16 concurrent duplicates creating exactly one row, in 3 repetitions.

**What is idempotency?**
Performing an operation N times has the same effect as performing it once. In PayFlow, creation, cancellation, authorization, processing, settlement submission, ledger posting and fraud assessment are all idempotent by design.

**How do concurrent duplicate requests behave?**
1. Both miss the fast-path lookup.
2. Both INSERT. PostgreSQL makes the second INSERT wait on the unique index until the first transaction commits, then it fails with 23505.
3. The loser rolls back, re-reads the committed winner and replays it (201 with `Idempotent-Replayed: true`).
4. Same key with a different payload returns 422.

**Optimistic vs pessimistic locking?**
- Optimistic (a version column, conditional UPDATE) holds no locks and fits low contention, which is the case for a single payment.
- Pessimistic (`SELECT … FOR UPDATE`) fits hot rows with frequent conflicts, but holds locks and risks deadlocks.

PayFlow uses optimistic locking. The one natural hot row, an account balance, is avoided by an append-only ledger with derived balances.

**What does `@Transactional` actually guarantee?**
That a Spring proxy begins, commits or rolls back a transaction around a *public method call through the proxy*. Its limits:
- Self-invocation bypasses it.
- By default only unchecked exceptions roll back.
- Isolation is the database default unless stated.
- It gives no protection against lost updates.

That is why PayFlow uses an explicit `TransactionRunner` and version checks.

**Where should transaction boundaries be?**
Around one aggregate's state change plus what must be atomic with it (the payment and its idempotency key). Never around network calls: fraud, settlement and ledger calls sit *between* short transactions, and unit tests assert `tx.isActive()==false` during them.

**Why not distributed transactions (XA)?**
- MongoDB is not an XA resource.
- 2PC holds locks across the network, and a failed coordinator leaves transactions "in doubt".
- It couples availability, so any participant being down stops payments.

We use local transactions with idempotent, resumable steps now, and the Outbox and Sagas in WP-02.

**Why not expose JPA entities as API DTOs?**
- It couples the public contract to the table layout.
- It risks lazy-loading and serialization surprises.
- It enables over-posting (mass assignment).
- It leaks internals such as the version.

ArchUnit forbids any class outside persistence adapters from depending on an `@Entity`.

## Patterns

**Why the Strategy pattern?**
Risk rules and settlement rails vary independently. Each is a class behind `RiskRule` or `SettlementGatewayPort`. The router fails startup if a rail has no gateway. Adding UPI Lite is one new class.

**Why the Adapter pattern?**
To isolate technology and external APIs behind our own port: JPA, MongoDB, provider SDKs and other contexts (the ACLs). The ACL adapter is also exactly where WP-03 resilience decorators will go.

**Why the Repository pattern?**
Use cases think in aggregates ("find this payment, update it"), not SQL. The port hides the persistence model and makes use cases testable with an in-memory fake that enforces the same version contract.

## Zero Trust

**What does Zero Trust mean for PayFlow?**
- **Verify explicitly:** RS256 JWT with exact iss, aud=payflow-api, exp and sub on every request.
- **Least privilege:** per-route scopes, deny by default, an orchestrator that can only process, a DML-only database role, and a container with no capabilities and a read-only filesystem.
- **Assume breach:** ownership checks in use cases returning 404, no secrets in the repo, validated log inputs, and no token-failure details in responses.

**Why isn't internal Kubernetes traffic automatically trusted?**
Network location is not identity. A single compromised pod, an SSRF bug or a misrouted Ingress would otherwise inherit full access. Every workload presents its own scoped token (the orchestrator's client credentials). mTLS and NetworkPolicies are added later as extra layers, not replacements.

## Failure scenarios

**What happens when MongoDB is unavailable?**
- Authorization fails closed with 503 and `Retry-After` in about 2 seconds (short driver timeouts), and the payment stays CREATED.
- Creation, reads, cancellation, processing of already-authorized payments and the ledger keep working.
- Readiness excludes MongoDB, so pods stay in rotation.
- A retry after recovery gets the same idempotent decision.

This was verified live against the Docker stack.

**What happens when PostgreSQL is unavailable?**
- Readiness goes DOWN, so pods leave the load balancer. Liveness stays UP, so there is no restart storm.
- Requests fail fast on the 3s pool acquisition timeout.
- No partial writes are possible, because every write is a local transaction.

**What if the application crashes immediately after the PostgreSQL COMMIT but before notifying another service?**
This is the **dual-write / database-event publication problem**: two systems (database and broker, or two contexts) cannot be updated atomically without a shared transaction. In WP-01 it appears concretely: a payment is SETTLED, then the process dies before posting the ledger or publishing `PaymentSettled`.

WP-01 mitigations:
- Every downstream step is idempotent (the unique journal reference).
- `process` re-ensures the ledger posting for SETTLED payments.
- The event port is already called *inside* the business transaction.

**WP-02 solves it with the Transactional Outbox.** The event is inserted into `payment.outbox` in the same transaction as the status change, and a relay (polling or Debezium CDC) publishes it to Kafka at least once. Idempotent consumers (Ledger, Fraud, Settlement) and Saga orchestration then give eventual consistency without 2PC.
