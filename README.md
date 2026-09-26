# PayFlow

A distributed payment-processing platform, built as a Principal Engineer system-design lab.

| Work package | Scope | Status |
|---|---|---|
| **WP-01** | Backend foundation and domain architecture | complete |
| **WP-02** | Distributed event-driven processing: Kafka (KRaft), Transactional Outbox, orchestrated Saga with funds reservation and compensation, idempotent consumers, retry/DLT, schema evolution | complete |

- Java 25, Spring Boot 4.1.1, Spring Kafka 4.1
- PostgreSQL 18, MongoDB 8, Kafka 4.2 (KRaft), Keycloak (OIDC)
- Clean Architecture per bounded context, enforced by ArchUnit
- Testcontainers-based failure engineering

## Build and test

Requires JDK 25 and a running Docker (Testcontainers starts PostgreSQL, MongoDB and Kafka).

```bash
./gradlew clean build
```

The build runs 217 tests: domain, use cases, contracts/schema evolution, ArchUnit, and integration and failure tests. The failure tests include outbox crash windows, duplicate delivery, consumer crashes before and after commit, poison messages, DLQ replay, saga compensation, concurrent funds reservations, and paused MongoDB/PostgreSQL containers.

## Run locally

```bash
cp .env.example .env
```

Edit every secret in `.env`, then:

```bash
./gradlew bootJar
```

```bash
docker compose up --build
```

| Service | Address |
|---|---|
| API | http://localhost:8080 (OpenAPI: `/v3/api-docs`, `/swagger-ui.html`) |
| Keycloak | http://localhost:8081, realm `payflow`: users `alice` and `bob` (client `payflow-customer-app`); client-credential identities `payflow-treasury` (deposits only) and `payflow-ops` (read/admin, DLQ replay, metrics) |
| Kafka | localhost:9092 (KRaft, single node) |

Payment flow (asynchronous):
1. `POST /api/v1/accounts`.
2. `POST /api/v1/accounts/{id}/deposits` with the treasury token.
3. `POST /api/v1/payments` with an `Idempotency-Key`. The response is 201 with status `CREATED`.
4. Poll `GET /api/v1/payments/{id}` until `SETTLED`, `REJECTED` or `FAILED`.
5. Operators use `GET /api/v1/payments/{id}/saga` and `POST /api/v1/ops/dead-letters/replay`, plus `/actuator/metrics` (for example `payflow.outbox.backlog`).

## Documentation

| Topic | Document |
|---|---|
| High-level design | [docs/architecture/HLD.md](docs/architecture/HLD.md) |
| WP-02 design: event architecture, topics, contracts, saga, exceptions, logging | [WP-02-LLD](docs/architecture/WP-02-LLD.md) · [EVENT-ARCHITECTURE](docs/architecture/EVENT-ARCHITECTURE.md) · [KAFKA-TOPIC-CATALOG](docs/architecture/KAFKA-TOPIC-CATALOG.md) · [EVENT-CONTRACTS](docs/architecture/EVENT-CONTRACTS.md) · [SAGA-DESIGN](docs/architecture/SAGA-DESIGN.md) · [EXCEPTION-ARCHITECTURE](docs/architecture/EXCEPTION-ARCHITECTURE.md) · [LOGGING-STANDARD](docs/architecture/LOGGING-STANDARD.md) |
| WP-01 design | [WP-01-LLD](docs/architecture/WP-01-LLD.md) · [CLEAN-ARCHITECTURE](docs/architecture/CLEAN-ARCHITECTURE.md) · [DATA-ARCHITECTURE](docs/architecture/DATA-ARCHITECTURE.md) · [ZERO-TRUST](docs/architecture/ZERO-TRUST.md) · [PATTERN-CATALOG](docs/architecture/PATTERN-CATALOG.md) |
| Decisions (ADR-000…016) | [docs/adr](docs/adr) |
| Failure matrix | [docs/failures/WP-02-FAILURE-MATRIX.md](docs/failures/WP-02-FAILURE-MATRIX.md) |
| Architecture reviews | [WP-01](docs/reviews/WP-01-ARCHITECTURE-REVIEW.md) · [WP-02](docs/reviews/WP-02-ARCHITECTURE-REVIEW.md) |
| Interview defense | [WP-01](docs/interview/WP-01-INTERVIEW-DEFENSE.md) · [WP-02](docs/interview/WP-02-INTERVIEW-DEFENSE.md) |
| Kafka ACL matrix (as code) | [deploy/kafka/acl-matrix.sh](deploy/kafka/acl-matrix.sh) |

## Roadmap

**WP-03** covers:
- Resilience4j around outbound adapters and the rail gateways
- relay batching or Debezium CDC
- a ledger/balance reconciliation job
- MANUAL_REVIEW tooling
- load testing, SLI/SLO, p95/p99
- the full observability platform
- chaos engineering
