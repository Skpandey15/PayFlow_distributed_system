# ADR-023: Kafka authentication (SASL/SCRAM-SHA-512) and deny-by-default ACLs; TLS deferred

- Status: Accepted (WP-03, narrows WP-02 K1)
- Date: 2026-09-27

## Context

WP-02 accepted K1:
- The local broker is PLAINTEXT with no authorizer, so any process on the network can read personal data from
  `fraud.commands` or forge `funds.events`.
- The mitigations were application-level only: producer ownership checks and consumer checks of producer and
  topic.
- The reviewed ACL matrix existed as a script but was never applied.

## Decision

1. **Authentication:**
   - Every client listener (docker network `INTERNAL`, host `EXTERNAL`) is `SASL_PLAINTEXT` with SCRAM-SHA-512.
   - Credentials are created in KRaft metadata at start by `deploy/kafka/bootstrap-security.sh`.
   - Passwords come only from the environment (`.env` locally, Secrets in Kubernetes).
2. **Authorization:** `StandardAuthorizer`, `allow.everyone.if.no.acl.found=false`.
3. **Identities:**

   | Principal | Grants |
   |---|---|
   | `payflow-app` (the monolith's single runtime identity) | Create, Describe, Read and Write on the prefixes `payment.`, `fraud.`, `funds.`, `settlement.` (including its retry and DLT topics); Read and Describe on its five consumer groups. Nothing else: no cluster ALTER, no foreign topics. |
   | `payment-service`, `fraud-service`, `account-service`, `settlement-service`, `ledger-service` | The WP-02 ACL matrix, applied for real. These are the identities after extraction; least privilege is enforceable and verifiable today. |
   | `acl-probe` | Authenticated, granted nothing (verifies deny-by-default). |

4. **Bootstrap:**
   - A loopback-only `LOCAL` listener (127.0.0.1:9094, inside the broker container) has the super-user principal.
     It is needed because KRaft SCRAM users must be created before anyone can authenticate.
   - The controller and inter-broker traffic of the single node also use loopback or PLAINTEXT listeners that
     are unreachable from the network.
5. **Verification:** `deploy/kafka/verify-security.sh` probes from separate client containers. Results are in
   WP-03-FAILURE-MATRIX and the evidence file.

## Not done, explicitly

| Gap | Where it is addressed |
|---|---|
| **TLS.** SCRAM protects passwords (salted challenge-response, never sent in clear) but not the payloads: traffic on the docker network is readable by anyone on that network. | Production: `SASL_SSL` (or mTLS with per-service certificates from cert-manager / SPIFFE). Requires certificate management, not built for a lab (the WP-03 brief asks not to). |
| **One application identity.** A compromised PayFlow process can still write any PayFlow command topic. This is inherent to the modular monolith. | Per-service identities take effect on extraction. The application-level ownership checks from WP-02 remain. |
| **Multi-broker replication security.** | Single node here; production brokers use SASL_SSL or mTLS for the inter-broker listener. |
| **Credential rotation.** | SCRAM supports multiple credentials per user, but rotation procedure and automation are future platform work. |
| **Testcontainers ITs** | Still run PLAINTEXT (the security controls are verified against the compose broker, not in every build). |

## Alternatives

| Alternative | Why not |
|---|---|
| mTLS now | Strongest (identity plus encryption). Needs a CA, issuance and rotation: "huge certificate complexity for a demo", explicitly out of scope. |
| SASL/PLAIN | Password sent on every connection; without TLS it is readable. |
| OAUTHBEARER with Keycloak | Attractive (one identity system), but token refresh inside the broker and clients adds moving parts; revisit with extraction. |

## Operational consequences

- `docker compose up` now starts a secured broker. Host tools need SCRAM client properties (see verify-security.sh).
- The broker health check waits for the security bootstrap, so the app never starts against a broker without
  its identity.
- The app runs with Spring profile `kafka-sasl` (credentials via `PAYFLOW_KAFKA_USERNAME` / `PAYFLOW_KAFKA_PASSWORD`).

## Failure implications

| Failure | Behaviour |
|---|---|
| Wrong or rotated password | Clients fail authentication. Producers: outbox backlog grows and admission control engages. Consumers: lag grows. Both are alerting paths that already exist. |
| Missing ACL after a new topic is added | The app fails to create or describe it at startup (fail fast at deploy time, not at the first payment). |
