# ADR-005: Zero Trust foundation (OAuth2/OIDC resource server, scopes, object-level authorisation)

- Status: Accepted (WP-01)
- Date: 2026-09-26

## Context

PayFlow will run on Kubernetes, where "inside the cluster" is a network location and not an identity. A compromised pod, a misconfigured Ingress or a stolen service credential must not grant blanket access. Payment APIs are a prime target for Broken Object Level Authorisation (BOLA, OWASP API1): reading or cancelling someone else's payment by guessing its id.

## Decision

- **Verify explicitly.** Every API call carries an OAuth2 access token (JWT) issued by the OIDC provider (Keycloak locally). Spring Security validates the following on every request:
  - an RS256 signature against the IdP's JWKS (HS256 and `alg=none` are rejected)
  - an exact `iss`
  - `aud` containing `payflow-api`
  - `exp` and `nbf` with 30s of clock skew
  - a non-blank `sub`
- **Least privilege.**
  - Per-route scope rules are declared in one place (`SecurityConfiguration`). Every unmapped route is `denyAll`.
  - Scopes: `payments:read`, `payments:write`, `payments:process`, `payments:admin`, `accounts:read`, `accounts:write`, `accounts:admin`, `ledger:read` and `fraud:read`.
  - The customer client gets only customer scopes. The orchestrator workload gets only `payments:process`. Operations gets read/admin scopes.
- **Defence in depth.**
  - Use cases re-check the permission (`ForbiddenException.requirePermission`) and enforce ownership (initiator or admin). Callers without visibility get **404**, not 403, so ids cannot be probed.
  - Payers can only pay from accounts they own.
- **Assume breach.**
  - Database runtime role with DML only (no DDL, no UPDATE/DELETE on the ledger).
  - MongoDB user limited to one database.
  - Containers run non-root with a read-only root filesystem, `cap_drop: ALL` and `no-new-privileges`.
  - No secrets in the repository.
  - Correlation-id input is validated to prevent log injection.
  - Error bodies never reveal why a token failed.

## Alternatives

1. **API gateway authentication only, with trusted internal traffic.** This is the perimeter model: one bypass exposes everything. Rejected.
2. **Opaque tokens with introspection.** Revocation is instant, but every request needs an IdP round-trip, and the IdP becomes a hard dependency on the hot path. We use short-lived JWTs (5 minutes) instead.
3. **mTLS only.** It authenticates workloads but not end users, and it carries no scopes. It is planned *in addition* (service mesh or SPIFFE) in the platform WPs.
4. **Role-based `hasRole` checks.** Roles are coarse. Scopes express delegated, least-privilege capabilities better. Ownership still has to be enforced in code.

## Trade-offs

- JWTs cannot be revoked before they expire. We mitigate this with a 5-minute lifetime and refresh-token revocation at the IdP.
- Two authorisation layers must be kept in sync: route scopes and use-case checks. Both are tested (`SecurityIT`, the use-case unit tests).
- Keycloak is an extra runtime component, but only locally. In EKS it can be a managed IdP.

## Consequences and future work (not in WP-01)

- Workload identity: Kubernetes ServiceAccount token projection or SPIFFE/SPIRE, exchanged for scoped tokens.
- mTLS between pods (mesh), NetworkPolicies (default deny), Kafka ACLs per topic (WP-02), secrets from Vault or External Secrets, and audit trails.
- Dev-only relaxations are listed in the review: the password grant on the customer client, and public Swagger UI.
