# PayFlow

A distributed payment-processing platform, built as a Principal Engineer system-design lab.

| Work package | Scope | Status |
|---|---|---|
| **WP-01** | Backend foundation and domain architecture | complete |
| **WP-02** | Distributed event-driven processing: Kafka (KRaft), Transactional Outbox, orchestrated Saga with funds reservation and compensation, idempotent consumers, retry/DLT, schema evolution | complete |
| **WP-03** | Resilience, scale and production engineering: measured baseline and bottlenecks, pipelined outbox relay, bounded intake and backpressure, Resilience4j at the settlement-rail boundary, SLI/SLO and error budgets, Prometheus/Grafana/Tempo, manual-review operations, continuous reconciliation, Kafka SASL/SCRAM + ACLs, Kubernetes manifests, failure campaign | complete |

- Java 25, Spring Boot 4.1.1, Spring Kafka 4.1, Resilience4j 2.4
- PostgreSQL 18, MongoDB 8, Kafka 4.2 (KRaft, SASL/SCRAM, ACLs), Keycloak (OIDC)
- Prometheus, Grafana, Tempo (OpenTelemetry), k6
- Clean Architecture per bounded context, enforced by ArchUnit
- Testcontainers-based failure engineering

## Build and test

Requires JDK 25 and a running Docker (Testcontainers starts PostgreSQL, MongoDB and Kafka).

```bash
./gradlew clean build
```

The build runs 239 tests: domain, use cases, contracts/schema evolution, ArchUnit, and integration and failure tests. The failure tests include:
- outbox crash windows, duplicate delivery, consumer crashes before and after commit, poison messages, DLQ replay
- saga compensation, concurrent funds reservations, paused MongoDB/PostgreSQL containers
- WP-03 additions: settlement-rail timeouts, retries, circuit breaker and bulkhead against a real HTTP rail simulator; manual-review resolution; reconciliation drift; rate limiting; admission control with a paused Kafka broker

## Run locally

```bash
cp .env.example .env
```

Edit every secret in `.env`, then:

```bash
./gradlew bootJar :rail-simulator:jar
```

```bash
docker compose up --build
```

| Service | Address |
|---|---|
| API | http://localhost:8080 (OpenAPI: `/v3/api-docs`, `/swagger-ui.html`) |
| Keycloak | http://localhost:8081, realm `payflow`: users `alice` and `bob` (client `payflow-customer-app`); client-credential identities `payflow-treasury` (deposits only) and `payflow-ops` (read/admin, DLQ replay, metrics) |
| Kafka | localhost:9092 (KRaft, single node, SASL/SCRAM-SHA-512 + ACLs; verify with `deploy/kafka/verify-security.sh`) |
| Grafana | http://localhost:3000 (admin / `GRAFANA_ADMIN_PASSWORD`), folder "PayFlow": 9 dashboards |
| Prometheus | http://localhost:9090 (alerts: `/alerts`) |
| Settlement rail simulator | http://localhost:8090 (lab only; fault injection: `POST /admin/faults`) |

Payment flow (asynchronous):
1. `POST /api/v1/accounts`.
2. `POST /api/v1/accounts/{id}/deposits` with the treasury token.
3. `POST /api/v1/payments` with an `Idempotency-Key`. The response is 201 with status `CREATED`.
4. Poll `GET /api/v1/payments/{id}` until `SETTLED`, `REJECTED` or `FAILED`.
5. Operators use `GET /api/v1/payments/{id}/saga`, `POST /api/v1/ops/dead-letters/replay`, the manual-review API (`/api/v1/ops/manual-reviews`, scope `ops:manual-review`) and reconciliation (`/api/v1/ops/reconciliation`, scope `ops:reconciliation`).

Load tests (k6, open model) and failure experiments:

```bash
ENV_FILE=.env performance/scripts/provision-perf-users.sh 50
```

```bash
ENV_FILE=.env performance/scripts/run.sh normal mylabel
```

Each run writes `performance/results/<run-id>/report.md`. The failure experiments are in `performance/scripts/failure-campaign.sh`.

## Documentation

| Topic | Document |
|---|---|
| High-level design | [docs/architecture/HLD.md](docs/architecture/HLD.md) |
| WP-02 design: event architecture, topics, contracts, saga, exceptions, logging | [WP-02-LLD](docs/architecture/WP-02-LLD.md) · [EVENT-ARCHITECTURE](docs/architecture/EVENT-ARCHITECTURE.md) · [KAFKA-TOPIC-CATALOG](docs/architecture/KAFKA-TOPIC-CATALOG.md) · [EVENT-CONTRACTS](docs/architecture/EVENT-CONTRACTS.md) · [SAGA-DESIGN](docs/architecture/SAGA-DESIGN.md) · [EXCEPTION-ARCHITECTURE](docs/architecture/EXCEPTION-ARCHITECTURE.md) · [LOGGING-STANDARD](docs/architecture/LOGGING-STANDARD.md) |
| WP-01 design | [WP-01-LLD](docs/architecture/WP-01-LLD.md) · [CLEAN-ARCHITECTURE](docs/architecture/CLEAN-ARCHITECTURE.md) · [DATA-ARCHITECTURE](docs/architecture/DATA-ARCHITECTURE.md) · [ZERO-TRUST](docs/architecture/ZERO-TRUST.md) · [PATTERN-CATALOG](docs/architecture/PATTERN-CATALOG.md) |
| WP-03 design: resilience, observability | [WP-03-LLD](docs/architecture/WP-03-LLD.md) · [RESILIENCE-ARCHITECTURE](docs/architecture/RESILIENCE-ARCHITECTURE.md) · [OBSERVABILITY-ARCHITECTURE](docs/architecture/OBSERVABILITY-ARCHITECTURE.md) |
| WP-03 performance | [WORKLOAD-MODEL](docs/performance/WORKLOAD-MODEL.md) · [WP-03-BASELINE](docs/performance/WP-03-BASELINE.md) · [BOTTLENECK-ANALYSIS](docs/performance/BOTTLENECK-ANALYSIS.md) · [TUNING-RESULTS](docs/performance/TUNING-RESULTS.md) · [CAPACITY-PLAN](docs/performance/CAPACITY-PLAN.md) |
| WP-03 SRE | [SLI-SLO](docs/sre/SLI-SLO.md) · [ERROR-BUDGET](docs/sre/ERROR-BUDGET.md) · [ALERTS](docs/sre/ALERTS.md) · [RUNBOOKS](docs/sre/RUNBOOKS.md) |
| Decisions (ADR-000…024) | [docs/adr](docs/adr) |
| Failure matrices | [WP-02](docs/failures/WP-02-FAILURE-MATRIX.md) · [WP-03](docs/failures/WP-03-FAILURE-MATRIX.md) |
| Architecture reviews | [WP-01](docs/reviews/WP-01-ARCHITECTURE-REVIEW.md) · [WP-02](docs/reviews/WP-02-ARCHITECTURE-REVIEW.md) · [WP-03](docs/reviews/WP-03-ARCHITECTURE-REVIEW.md) |
| Interview defense | [WP-01](docs/interview/WP-01-INTERVIEW-DEFENSE.md) · [WP-02](docs/interview/WP-02-INTERVIEW-DEFENSE.md) · [WP-03](docs/interview/WP-03-INTERVIEW-DEFENSE.md) |
| Kubernetes | [deploy/k8s](deploy/k8s) (base + k3d-lab overlay; `deploy.sh` then `smoke.sh` deploys to a throwaway k3d cluster and settles one payment) |
| Kafka ACL matrix (as code) | [deploy/kafka/acl-matrix.sh](deploy/kafka/acl-matrix.sh) |

## Roadmap

Next (from the WP-03 review):
- Pause the settlement consumer while the rail circuit is open (review R-2), and adaptive admission (P-2).
- Per-service extraction with per-service Kafka identities and databases.
- TLS/mTLS for Kafka.
- Sharded relay or CDC and sub-accounts for 10×.
- Incremental reconciliation and finance-approved correction journals.
- Four-eyes approval for manual review.
- Deployment to a real cluster with operators for the stateful services.
