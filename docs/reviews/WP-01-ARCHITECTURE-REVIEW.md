# WP-01 Architecture Technical Review

- Date: 2026-09-26
- Scope: all WP-01 code, migrations, configuration, Docker runtime and documentation
- Method: challenge each major decision with the questions *why, why not, trade-off, failure, concurrency, 10x, security, observability, testing*. This review is separate from ArchUnit: ArchUnit checks dependencies, this review checks decisions.

## Evidence base

| Evidence | Result |
|---|---|
| `./gradlew clean build` | **BUILD SUCCESSFUL**, **143 tests, 0 failed, 0 skipped** (Java 25, Spring Boot 4.1.1, PostgreSQL 18.1 and MongoDB 8.0 via Testcontainers) |
| ArchUnit | 20 rules pass; 4 negative tests prove the rules detect violations |
| Concurrency | `IdempotencyConcurrencyIT`: 16 concurrent duplicates × 3 runs gave 1 payment each, with real `pk_idempotency_record` collisions in the PostgreSQL log. `ProcessConcurrencyIT`: 8 concurrent processors × 3 runs gave 7 optimistic-lock conflicts, **1** settlement, **1** journal and correct balances |
| Live stack (`docker compose`) | Keycloak-issued tokens drove the whole flow: create (201), replay (201 + `Idempotent-Replayed: true`), authorize, process → SETTLED, balances −42.10 / +42.10. BOLA gave 404, the wrong scope 403, no token 401 |
| Live least privilege | Runtime DB role: `permission denied for table ledger_entry`, `must be owner of table payment`, no Flyway access |
| Live failure test | MongoDB stopped: authorize gave 503 in about 2.3s with `Retry-After`; payment stayed CREATED; readiness UP; creation still 201; retry after recovery gave AUTHORIZED |
| Observability | ECS JSON logs carry `traceId`, `spanId`, `correlationId` and domain-event key/values |

## Findings

Classification: **BLOCKER** means WP-01 cannot close. **MAJOR** must be fixed or explicitly accepted with justification. **MINOR** is tracked. **OBSERVATION** is informational.

### Resolved during the review (were BLOCKER or MAJOR)

| ID | Was | Finding | Resolution |
|---|---|---|---|
| R1 | BLOCKER | An invalid `Idempotency-Key` header returned **500**, because class-level `@Validated` sent validation through the AOP `MethodValidationInterceptor` (`ConstraintViolationException`), bypassing MVC's handler | `@Validated` removed; Spring MVC 7 built-in method validation now returns 400 with field-level `errors[]`; a defensive `ConstraintViolationException` → 400 handler was added. Covered by `PaymentApiIT.malformedRequests…` |
| R2 | MAJOR | Spring Data repositories declared as nested interfaces would not be scanned (`considerNestedRepositories=false`) | Moved to top-level package-private interfaces; ArchUnit enforces `notBePublic` |
| R3 | MAJOR | Expected unique-violation races logged at ERROR by Hibernate (`SqlExceptionHelper`), causing alert fatigue and masking real errors | That logger is silenced. Adapters translate violations, and real failures are logged once at the API boundary |
| R4 | MINOR | The fraud index initializer used `getBean` (service locator) | Replaced with a constructor-injected `FraudIndexInitializer` |
| R5 | MAJOR | The Docker build recompiled inside the image (untested bytes), and in this environment could not download the Gradle distribution | The image now packages the **tested** jar ("build once, ship the tested artifact"); the build context is only that jar |

**Open BLOCKERs: none.**

### MAJOR (open, explicitly accepted with justification)

| ID | Finding | Why accepted for WP-01 | Mitigation now | Owner |
|---|---|---|---|---|
| M1 | **Dual write between the payment commit and the ledger posting or event publication.** A crash after the SETTLED commit and before `ledger.post` leaves a settled payment without a journal entry. `LoggingPaymentEventPublisher` is not durable | Closing it properly needs the Transactional Outbox, which is WP-02 by scope. XA is rejected (ADR-004) | Posting is idempotent (`uq_journal_entry_reference`); re-invoking `process` on a SETTLED payment re-ensures it; the publisher port is already called in-transaction, so the Outbox is a drop-in | WP-02 |
| M2 | **No funds control.** Authorization checks eligibility and risk but not available balance, so ledger balances can go negative | Funds reservation spans Account/Ledger and Payment. Doing it synchronously across contexts would need a cross-context transaction or a lock. It is a Saga step (reserve, then capture or release) | Documented; balances are derived from an append-only ledger, so an overdraft is visible and auditable, never silent | WP-02 |
| M3 | **No automatic recovery of stuck PROCESSING payments or PENDING settlements.** Recovery requires a caller to re-invoke `process` | A sweeper or reconciler needs scheduling, leases and alerting, which belong to WP-02 (event re-drive) and WP-03 (reconciliation SLOs) | Every step is idempotent and resumable; `ix_settlement_status_updated` is ready for the sweeper | WP-02/03 |
| M4 | **Schema isolation between contexts is by convention plus ArchUnit, not enforced by the database.** All contexts share the `payflow_app` role, so a hand-written native query could read another schema | One role per context requires one datasource per context, which is premature in a single deployable | ArchUnit forbids cross-context dependencies; no cross-schema foreign keys exist; per-context roles come with extraction | Extraction WP |

### MINOR

| ID | Finding | Plan |
|---|---|---|
| m1 | The ledger balance is `SUM` over all lines, O(n) per account; hot merchant accounts degrade at scale | Periodic balance snapshots plus deltas since the snapshot (WP-03) |
| m2 | MongoDB indexes are created at startup and best-effort. If MongoDB is down at boot, the unique index may be missing until the next start | Check-first `findByPaymentId` narrows the window; move index creation to a migration job (Mongock) or deploy step |
| m3 | Idempotent replay returns the current representation, not a byte-identical copy of the original response | Documented in ADR-006. Store the response if partners require it |
| m4 | Simulated rails keep provider-side idempotency in memory (per JVM) | Simulation only; real providers keep it server-side |
| m5 | The idempotency purge runs on every replica | The DELETE is idempotent and indexed. ShedLock only if it becomes expensive |
| m6 | The API exposes domain enum names (`PaymentMethod`, `PaymentStatus`), coupling the contract to the domain | Acceptable for v1. Introduce API enums if the domain renames anything |
| m7 | The runtime-role grants migration (`R__`) must be edited whenever a table is added | Documented in the LLD; a CI check or `ALTER DEFAULT PRIVILEGES` per schema owner later |
| m8 | Dev-only settings: Keycloak password grant on the customer client, `start-dev` over HTTP, public Swagger UI | Listed in ZERO-TRUST §5; production profile or Helm values must disable them |
| m9 | No rate limiting or per-client quotas | WP-03 |
| m10 | No ETag / `If-Match` for client-side optimistic concurrency on cancel | Add ETag = version (low effort) |
| m11 | The event publisher runs inside the transaction and logs even when the transaction later rolls back | Replaced by the Outbox in WP-02 |
| m12 | The image is 459 MB, and the jar is 80 MB (OpenTelemetry, protobuf, springdoc) | Evaluate distroless or jlink and dependency trimming (platform WP) |

### OBSERVATIONS

- **O1: Account is close to CRUD.** Aggregate, snapshot and ports there are uniformity, not necessity. This is where DDD would become ceremony. We kept it for cheap extraction and consistent governance.
- **O2: Modular monolith (ADR-001).** The boundaries are real (ArchUnit, ACLs, no shared tables), so extraction is adapter work. The risk is that "we'll extract later" never happens; ADR-001 sets a revisit at WP-02 exit.
- **O3: Virtual threads plus a Hikari pool of 20.** The pool is the true database concurrency limit, and its 3s acquisition timeout turns saturation into fast failures. Pool sizing must be load-tested (WP-03).
- **O4: MongoDB in compose is a standalone mongod.** The majority read and write concerns are only meaningful on a replica set, which production requires. Testcontainers runs a single-node replica set.
- **O5: Test isolation uses unique ids per test, not cleanup.** The ledger is append-only by design, so cleanup is impossible without dropping the triggers, which is intentional.
- **O6: `VALIDATING` state rejected, and REJECTED/FAILED split** (LLD §2.3). This is a deliberate improvement on the proposed model.

## Decision challenges (abridged)

| Decision | Why | Why not the alternative | Failure / concurrency / 10x |
|---|---|---|---|
| Clean Architecture | Framework-free rules; explicit transactions; adapter swap for WP-02/03 | Layered design hides transactions and couples to JPA | More types. At 10x the architecture is neutral; the database is the limit |
| Modular monolith | No synchronous distributed coupling before the Outbox exists | Microservices now would multiply dual writes | Shared JVM failure domain; mitigated by fail-closed behaviour and timeouts |
| PostgreSQL for money | Constraints, ACID, unique-index arbitration | MongoDB lacks declarative invariants | Write throughput per primary is the first ceiling |
| MongoDB for fraud | Document-shaped, evolving evidence; separable | JSONB would also work (conceded in ADR-003) | Outage degrades only authorization (verified) |
| Optimistic locking | Low contention per payment; no held locks | `FOR UPDATE` holds locks across the request | Hot rows would need pessimistic locking or append-only; the ledger is append-only |
| Idempotency in PostgreSQL | Atomic with the payment; database arbitration | Redis dual write can lose keys on failover | One extra indexed insert per create |
| READ COMMITTED | Correctness from constraints and versions | SERIALIZABLE causes abort storms | — |
| JWT resource server + scopes + ownership | Verify every call; least privilege; BOLA-safe | Perimeter trust; roles too coarse | JWT revocation lag of at most 5 minutes |
| Explicit `TransactionRunner` | Boundaries visible and testable | `@Transactional` proxies, self-invocation traps | — |

## What breaks first at 10x traffic

1. **PostgreSQL primary write IOPS and connections.** Each create does 2 inserts plus index maintenance; each process does 5 or more writes across schemas. Remedies: PgBouncer, pool tuning, partitioning `payment` by time, then splitting databases per context.
2. **Hikari pool saturation.** 20 connections per pod with virtual threads means requests queue on the pool and fail after 3s. This needs load tests to size the pool (WP-03 p95/p99).
3. **Ledger balance queries (m1)** for hot accounts.
4. **Synchronous fraud and settlement in the request path.** Latency adds up. WP-02 makes them asynchronous.
5. **MongoDB velocity count** per authorization is index-backed but grows with velocity windows; consider pre-aggregated counters.

## Assumptions to validate under load (WP-03)

- Pool size versus virtual-thread concurrency, and the p99 of the create transaction.
- That the contention rate on a single payment really is low (the optimistic-lock conflict rate).
- MongoDB p99 under a 2s server-selection budget.
- Idempotency table growth over 24h and purge cost.
- JWKS caching behaviour during IdP key rotation.

## Principal Engineer Decision Matrix (reflecting the implementation)

| Problem | Options | Decision | Why | Trade-off |
|---|---|---|---|---|
| Architecture style | Layered / Clean / Hexagonal modules | Clean (ports and adapters) per context, ArchUnit-enforced | Dependency control; framework-free core | More types and mapping |
| Deployment unit | 5 services / monolith / modular monolith | Modular monolith (5 contexts, 1 deployable) | Avoid synchronous distributed coupling before the Outbox | Shared failure and scaling domain |
| Financial database | PostgreSQL / MongoDB | PostgreSQL | ACID, CHECK, unique arbitration, NUMERIC | Vertical write scaling; operational cost |
| Fraud evidence | PostgreSQL JSONB / MongoDB | MongoDB | Evolving documents; separable workload | Second technology; cross-store consistency is eventual |
| Money type | double / long minor units / BigDecimal | BigDecimal at currency scale, reject excess precision | Exact; readable; multi-scale currencies | Care needed with equals and scale (handled by `Money`) |
| Idempotency | Redis / app check / database PK | PostgreSQL PK, atomic with the payment | No dual write; arbitration under concurrency | Replay is not byte-identical |
| Concurrency | Optimistic / pessimistic | Optimistic plus unique constraints; append-only ledger | Low per-aggregate contention; no held locks | 409 retries under contention |
| Isolation | READ COMMITTED / SERIALIZABLE | READ COMMITTED | Races already arbitrated by indexes and versions | Must keep every race index-arbitrated |
| Distributed transaction | XA / eventual + idempotent steps | Eventual; Outbox in WP-02 | Autonomy; MongoDB is not XA-capable | Dual-write window (M1) until WP-02 |
| Cross-context calls | Shared tables / direct service calls / ports + ACL | Published inbound ports via outbound ACL adapters | Extraction becomes adapter work | Duplicate DTOs and enums |
| Authentication | Perimeter / mTLS / JWT | OAuth2 JWT (RS256) with iss/aud/exp validation | Verify every request, user and workload | Revocation lag |
| Authorization | Roles / scopes / scopes + ownership | Scopes at the edge plus ownership in use cases (404) | Least privilege; BOLA-safe | Two layers to keep in sync |
| Transaction demarcation | `@Transactional` / explicit port | `TransactionRunner` port | Visible boundaries; no Spring in the application layer | Slightly more verbose |
| Remote-call placement | Inside / outside the DB transaction | Always outside | No connection or lock pinning by slow dependencies | Intermediate states (PROCESSING/PENDING) must be resumable |

## Final Verification Matrix

| Requirement | Architecture | Implementation | Test / Evidence | Review finding | Status |
|---|---|---|---|---|---|
| Java 25 build | Gradle toolchain 25 | `build.gradle` | `./gradlew clean build` SUCCESS | — | PASS |
| Spring Boot 4.1.x | Boot BOM 4.1.1 | `build.gradle` | build and context load | — | PASS |
| Clean Architecture | ADR-000, CLEAN-ARCHITECTURE.md | package layout per context | `ArchitectureTest` (20) | O1 | PASS |
| Enforced by ArchUnit | 20 rules plus negative tests | `ArchitectureTest`, `ArchitectureRulesDetectViolationsTest` | 24 tests green; negatives fail as expected | — | PASS |
| Bounded contexts / service boundaries | ADR-001 | 5 contexts, ACL adapters | cross-context ArchUnit rules | O2, M4 | PASS (M4 accepted) |
| DDD domain model | LLD §2 | aggregates, VOs, domain service, events | `PaymentTest`, `JournalEntryTest`, `RiskScoringPolicyTest`, `SettlementTest`, `AccountTest`, `MoneyTest` | O1 | PASS |
| PostgreSQL persistence | ADR-002 | JPA adapters, 4 schemas | `PaymentPersistenceIT`, `LedgerPersistenceIT`, `ddl-auto=validate` in `ApplicationSmokeIT` | — | PASS |
| MongoDB where justified | ADR-003 | `MongoFraudAssessmentAdapter` | `FraudAssessmentMongoIT` (Decimal128, unique, velocity) | m2, O4 | PASS |
| Flyway migrations | DATA-ARCHITECTURE | V1–V4, R__ grants | smoke IT and live stack (migrator role) | m7 | PASS |
| Domain/persistence separation | CLEAN-ARCH rules 9–12 | entities, mappers, snapshots | ArchUnit rule 12; round-trip IT | — | PASS |
| REST APIs + versioning | LLD §6 | `/api/v1/**` controllers | `PaymentApiIT` (9), live curl | m6 | PASS |
| Validation | LLD §6 | Bean Validation plus `Money` | `malformedRequests…`, `currencyPrecision…` | R1 fixed | PASS |
| Standard errors (RFC 9457) | LLD §6 | `ApiExceptionHandler`, security handlers | problem+json assertions in API and security ITs | — | PASS |
| Pagination | LLD §6 | `PageQuery` / `PageResponse` | lifecycle test (list), `listingOnlyReturns…` | — | PASS |
| Correlation IDs | ZERO-TRUST §3 | `CorrelationIdFilter` | `everyResponseCarriesACorrelationId`; live ECS logs | — | PASS |
| OpenAPI | — | springdoc 3.1.1 | `openApiDocumentDescribes…` | m8 | PASS |
| Idempotency foundation | ADR-006 | PK-arbitrated, fingerprint, retention job | `IdempotencyConcurrencyIT`, `CreatePaymentServiceTest`, API IT, live replay | m3 | PASS |
| Concurrency protection | LLD §4 | versions plus unique constraints | `PaymentPersistenceIT.staleWrite…`, `ProcessConcurrencyIT` | — | PASS |
| Transaction boundaries | LLD §3 | `TransactionRunner` | unit tests assert remote calls happen outside transactions (`tx.isActive()==false`) | M1 | PASS (M1 accepted) |
| Design patterns | PATTERN-CATALOG | Strategy, Adapter, Template Method, ACL, … | router/rule/rail tests | — | PASS |
| SOLID | CLEAN-ARCH, interview doc | narrow ports; DIP via ports | ArchUnit; no god classes (largest use case, `CreatePaymentService`, is 130 lines including docs) | — | PASS |
| Zero Trust foundation | ADR-005, ZERO-TRUST.md | JWT, scopes, deny-all, ownership, least-privilege DB and container | `SecurityIT` (17), live role checks | m8, m9 | PASS |
| OAuth2/OIDC/JWT | ADR-005 | resource server; Keycloak realm | real-token tests; live Keycloak flow | — | PASS |
| Unit tests | — | domain and application | 70 unit tests (plus 24 architecture tests) | — | PASS |
| Integration tests + Testcontainers | — | PostgreSQL 18.1, MongoDB 8.0 | 49 integration tests (including 17 security) | — | PASS |
| Security tests | — | `SecurityIT` | 17 tests | — | PASS |
| Architecture tests | — | ArchUnit | 24 tests | — | PASS |
| Concurrency tests | — | 2 ITs × 3 repetitions | 1 payment; 1 settlement and 1 journal | — | PASS |
| Docker support | README | Dockerfile (layered, non-root), compose | `docker compose up` healthy; live E2E | R5, m12 | PASS |
| Observability foundation | HLD §6 | Actuator probes, ECS logs, trace context | smoke IT probes; live log sample | — | PASS |
| HLD / LLD / ADRs / catalog / review / interview | docs/ | 14 documents | this review | — | PASS |
| WP-02 extension points | HLD §7 | event port in-transaction; ACL ports; re-drivable steps | code plus ADR-004 | M1, M2, M3 | PASS (hand-over) |
| Funds control | — | not implemented | — | M2 | DEFERRED (accepted) |
| Automated recovery of stuck payments | — | not implemented | — | M3 | DEFERRED (accepted) |

## Verdict

**WP-01 is complete.** There are no open BLOCKERs. Four MAJOR findings are open, and each is explicitly accepted with justification and handed to WP-02 or the extraction WP:
- M1: dual write
- M2: funds control
- M3: automated recovery
- M4: database-level schema isolation

None of them is a correctness defect within WP-01's contract. They are the documented boundaries of synchronous, single-deployable processing.
