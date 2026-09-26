# ADR-000: Clean Architecture (Ports and Adapters) inside each bounded context

- Status: Accepted (WP-01)
- Date: 2026-09-26

## Context

PayFlow will change infrastructure several times on its roadmap:
- WP-02 adds Kafka, an Outbox and Sagas.
- WP-03 adds Resilience4j, caching and load work.
- Later WPs move to Kubernetes and EKS.

The payment rules themselves change for business reasons, not infrastructure reasons. Examples are the state machine, money precision, idempotency semantics and ownership rules. A layered `controller -> service -> repository` design binds use cases to JPA and to Spring's transaction proxies. Every infrastructure change then touches business code, and business code can only be tested with a Spring context.

## Decision

Each bounded context (`payment`, `account`, `fraud`, `ledger`, `settlement`) is split into four layers:

| Layer | Contents | Allowed dependencies |
|---|---|---|
| `domain` | aggregates, value objects, domain services, events, invariants | JDK and the shared kernel only |
| `application` | inbound ports (use-case interfaces), outbound ports, use-case services | domain and shared application types. No Spring, no JPA, no `@Transactional` |
| `adapter.in` / `adapter.out` | REST controllers; JPA/Mongo repositories; anti-corruption layers (ACLs) to other contexts; the event publisher | application ports, frameworks |
| `infrastructure` | composition root (`@Configuration`), schedulers, framework configuration | everything in its own context |

Transaction boundaries are explicit through the `TransactionRunner` outbound port, implemented once with `TransactionTemplate` in `platform.persistence`.

All of these rules are executable. `ArchitectureTest` holds 20 rules, and `ArchitectureRulesDetectViolationsTest` proves those rules are not vacuous.

## Alternatives

1. **Classic layered architecture with `@Service`/`@Transactional`.** Simpler and less code. However, the domain then depends on the persistence model, transactions are implicit (the self-invocation trap), and unit tests need Spring.
2. **Full hexagonal architecture with one Gradle module per layer and per context.** Compile-time enforcement is stronger than ArchUnit. The cost is about 20 modules for a 5-context lab and slower builds. ArchUnit gives the same guarantee at test time. We can move to modules when a context is extracted.
3. **Spring Modulith.** Good module-boundary verification. It does not express inner layering (domain versus application versus adapter), and it adds a framework dependency to the governance itself.

## Trade-offs

We gain:
- A framework-free domain and use cases. The application unit tests run in milliseconds with fakes.
- Replaceable adapters. Kafka, Resilience4j and a remote service can each arrive as a new adapter.
- Visible transaction boundaries.

We pay with:
- More types: ports, views, commands, and mappers for domain, JPA and web representations.
- Some duplication. `PaymentMethod` versus `SettlementMethod`, for example, exists on purpose because each context owns its own language.
- An onboarding cost for engineers used to layered Spring.

## Consequences

- New code must choose a layer. CI fails if a dependency points outward.
- The application layer cannot use `@Transactional`. It must use `TransactionRunner`. This is intentional because it forces the transaction-scope conversation to happen in code review.
- Over-engineering risk: the Account context is thin master data. We accept the ceremony there for uniformity (see review finding O1).
