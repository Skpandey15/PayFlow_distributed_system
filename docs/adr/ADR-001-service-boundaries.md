# ADR-001: Five bounded contexts, one deployable (modular monolith) in WP-01

- Status: Accepted (WP-01). Revisit at WP-02 exit.
- Date: 2026-09-26

## Context

The business capabilities are Payment, Account, Fraud, Ledger and Settlement. Each has its own language, invariants, data and rate of change, so they are bounded contexts. The question is whether each one should also be an independently deployable microservice now.

Settling one payment touches four contexts (Payment, Fraud, Settlement and Ledger). Without a message broker and an outbox, which arrive in WP-02, separate services could only coordinate through synchronous HTTP calls combined with dual writes. That produces all the failure modes of a distributed system without any of the tools to handle them.

## Decision

- **One deployable (`payflow`) containing five contexts** with enforced boundaries:
  - Packages per context, each with a Clean Architecture split inside.
  - A context may call another context only through that context's published inbound port (`<ctx>.application.port.in`), and only from its own outbound adapter, which acts as an Anti-Corruption Layer. ArchUnit enforces both rules.
  - No context reads another context's tables or collections.
- **Data ownership per context:**

  | Context | Owns |
  |---|---|
  | Payment | PostgreSQL schema `payment` |
  | Account | PostgreSQL schema `account` |
  | Ledger | PostgreSQL schema `ledger` |
  | Settlement | PostgreSQL schema `settlement` |
  | Fraud | MongoDB database `payflow_fraud` |

  There are no cross-schema foreign keys or joins.
- **No transaction spans two contexts.** Payment orchestrates, and each step commits in the context that owns the data.

## Evaluation per context

| Context | Separate service now? | Why / why not |
|---|---|---|
| Payment | No | It is the orchestrator. It would be the first service extracted once WP-02 gives it asynchronous collaborators. |
| Account | No | Thin master data and read-mostly. A network hop would add latency to every payment create and nothing else. |
| Fraud | Candidate, WP-02 | Different store (MongoDB), different scaling profile (CPU-bound scoring) and a different team in real organisations. It becomes a Kafka consumer of `PaymentCreated`. |
| Ledger | Candidate, WP-02 | It needs the strongest isolation (append-only, audited), but its input must be event-driven through the Outbox to avoid the dual write. |
| Settlement | Candidate, WP-02 or WP-03 | Its external rails need bulkheads and circuit breakers (WP-03). Isolating failure domains is the main reason to split it. |

## Alternatives

1. **Five microservices now.** This would demonstrate "microservices" but create synchronous coupling and unsolved dual writes. The system would be more fragile than the monolith, with no benefit yet.
2. **A single context ("PaymentService does everything").** This makes the later split expensive and hides the language differences, such as Fraud's "assessment" versus Payment's "authorization".
3. **A shared database with shared tables across services.** This is integration through the database, and it is rejected outright (ADR-004).

## Trade-offs

We gain:
- Local transactions inside each context.
- One deploy and one observability surface.
- Refactoring is cheap while boundaries are still being learned.

We lose:
- Independent scaling and deployment.
- Full failure isolation. A Fraud CPU spike shares the JVM with Payment (mitigated by per-call timeouts and fail-closed behaviour).

## Consequences

The extraction path is mechanical:
1. Replace the ACL adapter (for example `FraudContextAdapter`) with a remote client or an event consumer.
2. Give the context its own database role and instance.
3. Split its Flyway migrations.

The use cases do not change. That is the property ArchUnit protects.
