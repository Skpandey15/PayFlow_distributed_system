# Zero Trust in PayFlow (WP-01 foundation)

> Never trust, always verify. Least privilege. Assume breach.

## 1. Verify explicitly

The request flow is: User or workload → IdP (Keycloak, OIDC) → JWT → PayFlow → Spring Security → `Actor` → use case.

Every request is checked for:

| Check | Where | Test |
|---|---|---|
| RS256 signature against the IdP JWKS | `SecurityConfiguration.jwtDecoder` | `SecurityIT.tamperedTokenIsRejected`, `symmetricallySignedTokenIsRejected` |
| exact issuer | `tokenValidator` | `tokenFromAnotherIssuerIsRejected` |
| audience contains `payflow-api` | `tokenValidator` | `tokenForAnotherAudienceIsRejected` |
| exp/nbf with 30s skew | `tokenValidator` | `expiredTokenIsRejected` |
| non-blank `sub` | `tokenValidator` | `tokenWithoutSubjectIsRejected` |
| no token → 401 with `WWW-Authenticate: Bearer` | entry point | `missingTokenIsChallenged` |

- Security tests use **real signed tokens** and the production validator chain, not mocked authentication.
- The full stack was also verified live with Keycloak-issued tokens (password grant for demo users, client credentials for workloads).
- There is no fail-open path: without `payflow.security.jwt.jwk-set-uri` there is no `JwtDecoder` bean and the app refuses to start.

## 2. Least privilege

**Scopes per route**, declared in one place, with everything else `denyAll`:

| Identity | Client | Scopes |
|---|---|---|
| Customer (alice/bob) | `payflow-customer-app` (public, PKCE) | payments:read/write, accounts:read/write |
| Treasury funding | `payflow-treasury` (client credentials) | funds:deposit **only** (cannot read payments) |
| Ops / reconciliation | `payflow-ops` (client credentials) | payments:read/admin, accounts:read/admin, ledger:read, fraud:read, ops:dlq-replay, ops:metrics |

WP-02 removed the WP-01 `payflow-orchestrator` client and the `payments:process` scope. The payment workflow is now driven by the saga over Kafka, not by an HTTP caller.

Other controls:
- **Object-level authorisation** in the use cases, not only at the edge:
  - payments are visible to their initiator or `payments:admin`
  - accounts are visible to their owner or `accounts:admin`
  - you can only pay from your own account
  - non-visible objects return **404** (no enumeration)
  - evidence: `SecurityIT.ObjectLevelAuthorization`
- **Database:**
  - `payflow_app` has DML only.
  - The ledger is `SELECT, INSERT` only.
  - The idempotency table has `DELETE` (for purging).
  - There is no access to Flyway history.
  - Verified live: `permission denied for table ledger_entry`; `must be owner of table payment`.
- **MongoDB:** `payflow_fraud_app` has `readWrite` on `payflow_fraud` only.
- **Container:** UID 10001, read-only root filesystem, `cap_drop: ALL`, `no-new-privileges`, and ports bound to 127.0.0.1.

## 3. Assume breach

- Internal callers such as the orchestrator present tokens too. Nothing is trusted because it comes from inside the network.
- Use cases re-check permissions, so a future non-HTTP entry point (a Kafka consumer or a job) cannot skip authorisation.
- Secrets live only in the environment (`.env` is git-ignored; `.env.example` holds placeholders). Keycloak client secrets are injected into the realm import through placeholders.
- 401 bodies never state why a token failed. 5xx bodies contain only a correlation id.
- The correlation-id header is validated (`[A-Za-z0-9._-]{1,64}`) before it reaches the logs, which blocks log injection.
- Idempotency keys are namespaced per subject, so one client cannot discover another client's keys or payments.
- Fraud evidence contains IP addresses and device ids (personal data), so it is readable only with `fraud:read`.

## 4. Event backbone (WP-02)

**Verify explicitly, for messages too.** A consumer treats every record as untrusted input:
- The envelope must parse, and the type must be known.
- The **topic must be the type's topic** and the **producer must be the type's owner** (`EventCatalog`). A forged `FundsDeposited` claiming `payment-service` is dead-lettered UNTRUSTED_SOURCE, and the forged 1,000,000.00 never reaches the ledger (`ConsumerSemanticsIT`).
- The version must be supported, the record key must equal the aggregate id, and the payload must satisfy its contract.
- Business invariants are then re-checked by the participant. For example, reservation re-checks account status even though Payment already checked it (the check-then-act gap is tested).

**Least privilege on the producer side.** `EnvelopeFactory` refuses to build a message whose type the calling context does not own. Only `platform.messaging` may touch the Kafka producer (ArchUnit).

**Authentication and ACLs (design, see `deploy/kafka/acl-matrix.sh`):**
- One principal per service (SASL/SCRAM or mTLS; credentials from the secret store; short-lived where possible).
- Exactly one writer per topic. Read only on consumed topics.
- Prefixed rights only on the service's own `<topic>-<group>-*` retry and DLT topics.
- Default deny.
- TLS on every listener.

**Data minimisation.** Checkout evidence (IP, device) travels only on `fraud.commands`. `RiskAssessed` carries codes, not evidence. DLT records never contain stack traces or exception messages.

**Operator actions.** DLQ replay requires `ops:dlq-replay` and is audit-logged with the requesting subject. Metrics require `ops:metrics`. Deposits require `funds:deposit` (a separate treasury identity).

**Accepted gap (review K1).** Local Kafka is PLAINTEXT with no authorizer, and the monolith uses one Kafka identity. The ACL matrix is enforced when contexts deploy separately (platform WP).

## 4b. WP-03 hardening (implemented and verified)

| Control | Implementation | Evidence |
|---|---|---|
| Kafka authentication | SASL/SCRAM-SHA-512 on every client listener; credentials only from the environment | `deploy/kafka/verify-security.sh` (wrong password rejected; PLAINTEXT client refused) |
| Kafka authorization | StandardAuthorizer, deny by default; `payflow-app` limited to PayFlow topic prefixes and its groups; per-service principals with the WP-02 matrix | same script: an ungranted principal cannot write; ledger-service cannot read `fraud.commands`; fraud-service cannot forge `funds.events`; the app cannot create foreign topics |
| Metrics endpoint | `/actuator/prometheus` needs `ops:metrics`; Prometheus authenticates with its own client (`payflow-monitoring`, metrics scope only) | SecurityIT, live scrape |
| Operations authorization | separate scopes `ops:manual-review`, `ops:reconciliation`, `ops:dlq-replay` (least privilege, not "admin"); route rules plus use-case checks | ManualReviewIT, ReconciliationIT |
| Audit | manual-review decisions append-only (runtime role has INSERT/SELECT only) with operator, reason, ticket, frozen evidence | ManualReviewIT |
| Abuse protection | per-subject rate limits (payments 20/s; ops 10/min) | TrafficControlIT |
| Kubernetes | restricted Pod Security, non-root, read-only root FS, no capabilities, no ServiceAccount token, default-deny NetworkPolicy | `deploy/k8s` (rendered; not deployed to a cluster in WP-03) |
| Telemetry privacy | JDBC spans without parameter values; no ids as metric labels; DLT headers sanitized (WP-02) | configuration |

**Not production Zero Trust yet, stated plainly:**
- Kafka traffic is **not encrypted** (SASL_PLAINTEXT on the docker network; TLS/mTLS deferred, ADR-023).
- The monolith uses **one** Kafka identity.
- Keycloak runs in dev mode over HTTP.
- Secrets live in a local `.env`.
- The operator ops API has no four-eyes approval.
- The rail simulator's admin API is unauthenticated (lab-only test double).

## 5. Deliberately deferred (WP-02/03 and platform WPs)

| Capability | Plan |
|---|---|
| Workload identity | Kubernetes projected ServiceAccount tokens or SPIFFE/SPIRE, exchanged for scoped JWTs (short-lived credentials, no static client secrets) |
| mTLS | Service mesh (Istio or Linkerd) between pods. Complements, but does not replace, JWT authorisation |
| Network | Kubernetes NetworkPolicies, default deny |
| Kafka | ~~ACLs per topic and principal~~ done in WP-03 (ADR-023); remaining: TLS/mTLS, per-service identities at extraction, credential rotation |
| Secrets | External Secrets Operator or Vault. Database credentials rotated through dynamic secrets |
| Audit | An append-only audit trail of security-relevant actions (who cancelled or froze what) |
| Rate limiting | ~~Per-client quotas~~ per-subject limits done in WP-03 (in-process); cluster-wide quotas belong in the API gateway |

## 6. Local-development relaxations (must NOT reach production)

- The `payflow-customer-app` client has `directAccessGrantsEnabled` (password grant) for curl demos. Disable it; use Authorization Code with PKCE only.
- Keycloak runs `start-dev` over HTTP. Production needs TLS and a real database.
- Swagger UI and `/v3/api-docs` are public. Disable them with `springdoc.api-docs.enabled=false` in production.
- Kafka uses SASL_PLAINTEXT (authenticated and authorized, but unencrypted) and has a loopback-only bootstrap listener with a super user (ADR-023). Production: SASL_SSL or mTLS.
