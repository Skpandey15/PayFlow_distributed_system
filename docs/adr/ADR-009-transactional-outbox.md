# ADR-009: Transactional Outbox for every PostgreSQL state change that must be announced

- Status: Accepted (WP-02); closes WP-01 finding M1
- Date: 2026-09-27

## Context

The dual write:

```
COMMIT payment ✓  →  process crashes  →  publish to Kafka ✗     (event lost forever)
publish ✓          →  COMMIT fails                                (phantom event)
```

A database and a broker cannot be updated atomically without a distributed transaction (rejected in ADR-004).

## Decision

- Each producing context (payment, account, settlement) has an `outbox_event` table in its own schema.
- Outbound messaging adapters insert the full event envelope into it **in the same local transaction** as the business change. `OutboxWriter` refuses to run without an active transaction.
- A relay later publishes the rows (ADR-010).

Row layout:

| Column | Purpose |
|---|---|
| `id` BIGSERIAL | publication order |
| `event_id` UUID (unique) | idempotency key for consumers |
| `aggregate_type`, `aggregate_id`, `message_key` | routing and partitioning |
| `topic`, `event_type`, `event_version` | contract identity |
| `envelope` JSONB | full wire message |
| `correlation_id`, `traceparent` | captured at write time, so the trace survives the asynchronous hop |
| `created_at` | age metric |
| `published_at` | publication state |
| `publish_attempts`, `last_error` | retry metadata; sanitized class name only |

## Alternatives

| Option | Why not chosen |
|---|---|
| Publish after commit (`@TransactionalEventListener(AFTER_COMMIT)`) | Crash window between commit and send: loss. |
| Publish before commit | Phantom events on rollback. |
| Kafka transactions plus a DB transaction (best-effort 1PC) | Still two resources, and a crash between the two commits breaks atomicity. |
| XA/2PC | ADR-004. |
| Event sourcing (the event store is the source of truth) | Solves it structurally, but it is a much larger paradigm shift for a financial core already modelled relationally. |

## Trade-offs

We gain:
- Atomicity of state and announcement.
- No loss.
- Publication survives broker outages (verified live: the backlog grew during the outage and drained afterwards).

We accept:
- At-least-once publication (duplicates possible, so consumers dedupe).
- Publication latency equal to the poll interval (200 ms).
- Outbox tables to purge (7 days).
- The events are only as durable as the database: acceptable, since the database is our source of truth.

## Failure implications

| Crash point | Result | Evidence |
|---|---|---|
| Before commit | Nothing written anywhere (consistent) | — |
| After commit, before send | Row unpublished; the relay sends it later | `OutboxIT.eventsCommittedWhilePublisherIsDown…` |
| After send, before marking | Resent, consumers dedupe on eventId | `OutboxIT.republishedEventIsDeduplicated…` |
| Broker down | Rows accumulate; metrics `payflow.outbox.backlog` and `payflow.outbox.oldest.age.seconds` | `OutboxIT.unreachableBroker…`, plus the live test |

## Operational consequences

- **Alert:** oldest unpublished age > 60 s (WP-03 SLO wiring).
- **Purge:** published rows older than 7 days.
- **Ops control:** the relay can be paused (drain before broker maintenance).
- The Fraud context (MongoDB) is the exception: it uses consume-process-produce without an outbox (ADR-013).
