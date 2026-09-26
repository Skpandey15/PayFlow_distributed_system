# PayFlow

A distributed payment-processing platform, built as a Principal Engineer system-design lab.
**Current state: WP-01, Backend Foundation and Domain Architecture (complete).**

- Java 25, Spring Boot 4.1.1, Gradle
- PostgreSQL 18 (financial state), MongoDB 8 (fraud evidence)
- OAuth2/OIDC JWT (Keycloak)
- Clean Architecture per bounded context, enforced by ArchUnit
- Testcontainers-based tests

## Build and test

Requires JDK 25 and a running Docker (Testcontainers).

```bash
./gradlew clean build
```

The build runs the full suite: 143 tests (domain, use cases, ArchUnit, PostgreSQL and MongoDB integration, security, concurrency).

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

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| OpenAPI / Swagger UI | http://localhost:8080/v3/api-docs · http://localhost:8080/swagger-ui.html |
| Keycloak | http://localhost:8081 (realm `payflow`, users `alice` and `bob`, clients `payflow-customer-app`, `payflow-orchestrator`, `payflow-ops`) |

Get a customer token (dev-only password grant):

```bash
curl -s http://localhost:8081/realms/payflow/protocol/openid-connect/token -d grant_type=password -d client_id=payflow-customer-app -d username=alice -d password="$PAYFLOW_DEMO_USER_PASSWORD"
```

Main flow:
1. `POST /api/v1/accounts`
2. `POST /api/v1/payments` (with an `Idempotency-Key` header)
3. `POST /api/v1/payments/{id}/authorize` and then `/process` (orchestrator token)
4. `GET /api/v1/ledger/accounts/{id}/balance?currency=USD` (ops token)

## Documentation

| Topic | Document |
|---|---|
| High-level design | [docs/architecture/HLD.md](docs/architecture/HLD.md) |
| Low-level design (transactions, concurrency, API, failures) | [docs/architecture/WP-01-LLD.md](docs/architecture/WP-01-LLD.md) |
| Clean Architecture and ArchUnit rules | [docs/architecture/CLEAN-ARCHITECTURE.md](docs/architecture/CLEAN-ARCHITECTURE.md) |
| Patterns (implemented and rejected) | [docs/architecture/PATTERN-CATALOG.md](docs/architecture/PATTERN-CATALOG.md) |
| Zero Trust | [docs/architecture/ZERO-TRUST.md](docs/architecture/ZERO-TRUST.md) |
| Data architecture | [docs/architecture/DATA-ARCHITECTURE.md](docs/architecture/DATA-ARCHITECTURE.md) |
| ADRs 000–006 | [docs/adr](docs/adr) |
| Architecture Technical Review, decision and verification matrices | [docs/reviews/WP-01-ARCHITECTURE-REVIEW.md](docs/reviews/WP-01-ARCHITECTURE-REVIEW.md) |
| Interview defense | [docs/interview/WP-01-INTERVIEW-DEFENSE.md](docs/interview/WP-01-INTERVIEW-DEFENSE.md) |

## Roadmap

- **WP-02:** Kafka, Schema Registry, Transactional Outbox, Sagas (funds reservation), idempotent consumers, DLQ.
- **WP-03:** Resilience4j around the outbound adapters, rate limiting, load testing, SLI/SLO, p95/p99, full observability.
