# ADR-006: API idempotency with a database-arbitrated Idempotency-Key

- Status: Accepted (WP-01)
- Date: 2026-09-26

## Context

Clients retry: after timeouts, on mobile reconnects, from load-balancer retries, and after users double-click. A retried `POST /api/v1/payments` must never create a second payment. Concurrent duplicates can arrive while the first request is still in flight. This must work without Kafka.

## Decision

- **Contract.** `POST /api/v1/payments` requires an `Idempotency-Key` header: up to 255 characters from `[A-Za-z0-9._:-]`.
- **Scope.** Keys are unique per client, meaning the JWT `sub`. The primary key is `(client_id, idempotency_key)`. Clients cannot collide with, or probe, each other's keys.
- **Fingerprint.** A SHA-256 of the canonical, *parsed* request is stored with the key. The canonical form covers payer, payee, the amount normalised to the currency scale, currency, method and reference. Reusing a key with a different payload returns `422 IDEMPOTENCY_KEY_REUSED`.
- **Atomicity.** The payment row and the idempotency row are inserted in **one local transaction**. There is no window in which one exists without the other.
- **Concurrency.** The key is looked up first as a fast path. If it is absent, the service inserts. On a primary-key violation (`pk_idempotency_record`, SQLSTATE 23505), the request lost a race: it re-reads the winner's committed record and replays it. PostgreSQL makes the second INSERT *wait* for the first transaction, so the loser always finds a committed winner. No IN_PROGRESS state or lock table is needed, because the create transaction contains no remote calls and is short.
- **Replay semantics.** Replays return `201` with the payment and `Idempotent-Replayed: true`. They return the payment's **current** representation, not a byte-identical copy of the first response. That is safe because creation has no side effects beyond the payment itself, and the client gets fresher state.
- **Retention.** A key stays bound for at least 24 hours (`payflow.payment.idempotency-retention`). An hourly job deletes expired rows, using an index on `expires_at`. The job is idempotent, so it is safe on every replica.

## Alternatives

1. **Redis `SETNX` for keys.** It adds a store and a dual write between Redis and PostgreSQL. A Redis failover can lose keys and lead to duplicate payments. Rejected: the key must commit atomically with the payment.
2. **Check-then-insert in application code.** Two concurrent requests both see "absent" and both insert. That is the bug this ADR exists to prevent.
3. **Deduplicating in Kafka consumers.** Too late: the duplicate payment already exists. Consumer idempotency is a separate WP-02 concern.
4. **Storing the full response body.** It gives byte-identical replays, but stores web-layer JSON in the application layer and grows the table. We can revisit this if partners require it.
5. **Client-supplied payment id (PUT semantics).** A valid design, but the industry convention for payments APIs is an Idempotency-Key header.

## Trade-offs

- One extra indexed INSERT per create, plus a lookup.
- A key is burned even if the create fails *after* commit (not possible today).
- Replays are not byte-identical (documented).
- Keys older than the retention window can be reused, which is by design.

## Evidence

| Test | What it covers |
|---|---|
| `IdempotencyConcurrencyIT` | 16 threads × 3 repetitions release the same key simultaneously, producing exactly one row. PostgreSQL logged real `duplicate key ... pk_idempotency_record` collisions, which proves the race path executed |
| `CreatePaymentServiceTest` | replay, payload mismatch, race resolution, per-client scoping |
| `PaymentApiIT.idempotentReplayAndKeyReuseOverHttp` | HTTP semantics |
