# ADR-010: Polling outbox relay now; CDC (Debezium) as the production-scale path

- Status: Accepted (WP-02)
- Date: 2026-09-27

## Context

Outbox rows (ADR-009) must be moved to Kafka reliably and in per-aggregate order.

## Decision

A **polling relay** per outbox table (`OutboxRelay`), driven every 200 ms on every replica:

```
BEGIN
  pg_try_advisory_xact_lock(hash(outbox))   -- one active publisher per outbox cluster-wide
  SELECT unpublished ORDER BY id LIMIT 100  -- partial index on (id) WHERE published_at IS NULL
  for each row: send (acks=all, wait ≤ 5 s) → UPDATE published_at
                on failure: attempts+1, last_error, STOP (never skip ahead)
COMMIT
```

## Alternatives

| | Polling (chosen) | CDC with Debezium (WAL → Kafka Connect) |
|---|---|---|
| Moving parts | none beyond the app | Kafka Connect cluster, a Debezium connector, a replication slot |
| Latency | poll interval (about 200 ms) | tens of ms |
| Database load | index scan per poll per outbox | WAL decoding; a replication slot that can bloat WAL if Connect stops |
| Ordering | single writer + id order | WAL commit order (strict) |
| Throughput ceiling | about 1–2k msgs/s per outbox (sequential synchronous sends) | very high |
| Operability | trivial, testable in unit and integration tests | connector lifecycle, slot monitoring, schema of the change events |

Polling is chosen for the lab: it is correct, observable, and has no new infrastructure. **Debezium's outbox event router is the documented production-scale alternative**, adopted when publication throughput or latency becomes the bottleneck (the WP-03 load test decides). Moving to it changes no application code, because the outbox table is already the contract.

## Trade-off accepted: network I/O inside the relay transaction

The relay holds a transaction (advisory lock + row updates) while it waits for broker acks. This is a conscious exception to WP-01's "no network in transactions" rule. The rule protects **business** transactions (row locks on payments and balances, user-facing latency). The relay:
- touches only outbox rows that nobody else updates
- is bounded by batch × send timeout under a 30 s transaction timeout
- runs off the request path

The alternative, a lease-claim model that sends outside the transaction, needs a leader lease to keep ordering, which is more moving parts for no correctness gain.

## Failure implications

| Failure | Consequence |
|---|---|
| Relay replica dies mid-batch | The transaction rolls back and the advisory lock is released. Another replica takes over. Rows already acknowledged by Kafka but not yet marked are re-sent (duplicates, which consumers dedupe). |
| Slow broker | The batch stalls up to the send timeout, then stops and retries next poll. Order is preserved. |
| Database down | The relay poll fails and is logged WARN. Nothing is lost. |

## Operational consequences

- One busy outbox is served by one replica at a time, which is the throughput ceiling above.
- Watch `payflow.outbox.oldest.age.seconds`.
- Batching async sends with per-partition failure handling is the first optimisation before CDC.
