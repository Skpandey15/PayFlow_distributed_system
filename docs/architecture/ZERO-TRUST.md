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
| Payment orchestrator | `payflow-orchestrator` (client credentials) | payments:process **only** (cannot even read payments) |
| Ops / reconciliation | `payflow-ops` (client credentials) | payments:read/admin, accounts:read/admin, ledger:read, fraud:read |

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

## 4. Deliberately deferred (WP-02/03 and platform WPs)

| Capability | Plan |
|---|---|
| Workload identity | Kubernetes projected ServiceAccount tokens or SPIFFE/SPIRE, exchanged for scoped JWTs (short-lived credentials, no static client secrets) |
| mTLS | Service mesh (Istio or Linkerd) between pods. Complements, but does not replace, JWT authorisation |
| Network | Kubernetes NetworkPolicies, default deny |
| Kafka | ACLs per topic and principal (WP-02) |
| Secrets | External Secrets Operator or Vault. Database credentials rotated through dynamic secrets |
| Audit | An append-only audit trail of security-relevant actions (who cancelled or froze what) |
| Rate limiting | Per-client quotas (WP-03) |

## 5. Local-development relaxations (must NOT reach production)

- The `payflow-customer-app` client has `directAccessGrantsEnabled` (password grant) for curl demos. Disable it; use Authorization Code with PKCE only.
- Keycloak runs `start-dev` over HTTP. Production needs TLS and a real database.
- Swagger UI and `/v3/api-docs` are public. Disable them with `springdoc.api-docs.enabled=false` in production.
