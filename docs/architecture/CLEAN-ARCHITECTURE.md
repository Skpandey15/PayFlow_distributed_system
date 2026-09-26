# Clean Architecture in PayFlow

## Dependency rule

```
infrastructure (composition root) ─┐
adapter.in (web)  ─────────────────┼──▶ application (ports + use cases) ──▶ domain
adapter.out (persistence, ACL) ────┘            ▲ implements ports
```

Source dependencies point **inward only**:
- The domain knows nothing about the other layers.
- The application layer knows the domain and defines ports.
- Adapters implement ports and depend on frameworks.
- `infrastructure` wires everything together.

## Where Dependency Inversion is visible

```
CreatePaymentService ──uses──▶ PaymentRepositoryPort   (interface, application layer)
                                     ▲ implements
                         JpaPaymentRepositoryAdapter ──▶ Spring Data JPA ──▶ PostgreSQL
```

`PaymentSagaService` depends on `SagaCommandPort`, not on Kafka or on the other contexts. In WP-01 the implementation was an in-process Anti-Corruption Layer; in WP-02 it became `OutboxSagaCommandPublisher` (Transactional Outbox → Kafka) **without any change to the use case**. That swap is the concrete payoff of Dependency Inversion.

## Rules enforced by ArchUnit (`ArchitectureTest`)

| # | Rule | Why it matters |
|---|---|---|
| 1 | domain does not depend on Spring, JPA, Hibernate, Mongo, Servlet, Validation, Jackson, Swagger, SLF4J, Kafka or Resilience4j | business rules outlive frameworks |
| 2 | domain does not depend on application, adapter, infrastructure or platform | inward dependencies |
| 3 | application does not depend on those frameworks (so no `@Transactional`) | explicit transaction boundaries through the `TransactionRunner` port |
| 4 | application does not depend on adapter, infrastructure or platform | Dependency Inversion |
| 5 | onion architecture per context (domain / application / inbound / outbound) | adapters do not call each other |
| 6 | inbound adapters do not touch `adapter.out`, Spring Data, JPA or JDBC | controllers call use cases only |
| 7 | inbound adapters depend on ports, not `usecase` implementations | depend on abstractions |
| 8 | `@RestController` lives in `adapter.in.web` | discoverability |
| 9 | `@Entity` lives in `adapter.out.persistence` | persistence separation |
| 10 | `@Document` lives in `adapter.out.persistence` | persistence separation |
| 11 | Spring Data repositories live in persistence adapters and are **not public** | a repository is an adapter detail |
| 12 | no class outside persistence adapters depends on an `@Entity` or `@Document` | JPA entities are never exposed via REST |
| 13 | contexts only use each other's `application.port.in` | bounded-context isolation |
| 14 | cross-context calls only from `adapter.out` (ACL) | translation at the boundary |
| 15 | shared kernel depends on no context | bottom of the graph |
| 16 | platform depends on no context | reusable after extraction |
| 17 | no cycles between top-level slices | acyclic contexts |
| 18 | no `double`/`float`/`Double`/`Float` fields anywhere | money safety |
| 19 | no `new BigDecimal(double)` or `BigDecimal.valueOf(double)` | money safety |
| 20 | no `@Autowired` field injection; `@Entity` fields not public | explicit, testable dependencies |

`ArchitectureRulesDetectViolationsTest` runs rules 1, 6, 11 and 18 against deliberately violating fixtures (`com.payflow.archfixture`) and asserts that they **fail**. This prevents the classic failure mode of rules that pass because their package pattern matches nothing.

## WP-02 rules (event backbone)

| Rule | Why it matters |
|---|---|
| domain + application do not depend on contracts, platform.messaging, Spring Kafka or Kafka clients | the core stays unaware of topics, envelopes, offsets and retries |
| `@KafkaListener` methods only in `..adapter.in.messaging..` | consuming is an inbound adapter that calls application ports |
| only `platform.messaging` depends on `KafkaTemplate` | contexts publish through the outbox or `DirectEventPublisher`, never ad hoc |
| web controllers never depend on Kafka, messaging or contracts | HTTP changes state via use cases; events are a consequence |
| `contracts` depends only on `java..` | the published language leaks nothing |
| outbound messaging adapters never depend on `@Entity` | events are contracts, not serialized entities |

Negative fixtures (`KafkaAwareDomainObject`, `PublishingController`) prove these rules fail when violated. Total: 26 rules.

## Clean vs Hexagonal vs Onion, as used here

We use the **Ports and Adapters** vocabulary (inbound and outbound ports) and the **Clean/Onion** dependency rule (concentric layers with the domain at the centre). In practice they are the same idea: isolate the policy from the mechanisms.

## When this becomes over-engineering

- The Account context has 1–2 invariants, so a plain layered design would do. We keep the structure for uniformity and cheap extraction, and we acknowledge the cost (review finding O1).
- Mapping into three representations (web DTO, application view, JPA entity) is worth it for Payment, whose API and schema evolve independently. It is ceremony for pure lookups.
- If a service is CRUD with no invariants and one adapter, prefer a simple layered design.
