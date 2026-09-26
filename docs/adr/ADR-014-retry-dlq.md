# ADR-014: Classified, bounded, non-blocking retries; sanitised DLT per consumer group; controlled replay

- Status: Accepted (WP-02)
- Date: 2026-09-27

## Context

Consumers fail for different reasons, and the right response differs:

| Kind of failure | Right response |
|---|---|
| Transient (database or MongoDB blip, rail outage) | Retry |
| Permanent (malformed, unsupported version, business-rule conflict) | Never retry |
| Poison messages | Must not block their partition |
| Retries | Must not become a storm |

## Decision

1. **Classify at the consumer boundary.** `FailureClassifier` maps every failure to a `FailureCategory`:

   | Categories | Handling |
   |---|---|
   | DESERIALIZATION, CONTRACT_VIOLATION, UNSUPPORTED_VERSION, UNTRUSTED_SOURCE, BUSINESS_RULE, DATA_INTEGRITY | Permanent → DLT immediately |
   | CONCURRENCY, TRANSIENT_INFRASTRUCTURE, UNKNOWN | Retryable |

   It rethrows `PermanentEventProcessingException` or `TransientEventProcessingException`.
2. **Non-blocking retry topics** (`@RetryableTopic`, per consumer group):
   - `<topic>-<group>-retry-{0,1,2}` with exponential backoff 1 s → 3 s → 9 s (max 30 s), then `<topic>-<group>-dlt`.
   - `PermanentEventProcessingException` is excluded, so permanent failures go straight to the DLT.
   - Retries do not block the partition, so poison messages cannot stall it.
3. **The DLT record keeps diagnostics but never secrets.**
   - Kept: original topic, partition, offset, timestamp and consumer group (Spring headers); eventId, type, version, correlationId and traceparent (PayFlow headers); `payflow-failure-category`; stable `payflow-error-code`; attempts.
   - **Stack traces and exception messages are stripped**, because messages can contain data values.
4. **DLT observation:** one ERROR log and `payflow.events.dead_lettered{topic,consumer,category}`, which is the alerting signal.
5. **Replay:** `POST /api/v1/ops/dead-letters/replay` (scope `ops:dlq-replay`, audited) re-publishes one record to the failed group's **own `-retry-0`** topic, with the original eventId. Other groups are unaffected, and the consumer's inbox makes a replay at most once.

## Alternatives

| Option | Why not chosen |
|---|---|
| Blocking in-place retries (DefaultErrorHandler backoff) | Preserve order but block the partition. One poison or slow message stalls every payment on that partition. |
| Infinite retry | A retry storm, and hides permanent errors. |
| Shared retry topics across groups | One group's retry would be redelivered to every group. |
| DLT as a "bin" with no replay tooling | Accumulates silent financial inconsistencies. |

## Trade-offs

- **Ordering:** a retried record is processed after newer records of the same key. This is mitigated by order-tolerant handlers (saga step guards, reservation tombstone, capture rejects released funds). See ADR-008.
- More topics: 3 retry topics plus 1 DLT per (topic, group).

## Failure implications

| Failure | Handling |
|---|---|
| Retry exhaustion of a saga command (for example MongoDB down longer than the retry window) | DLT, then the saga stays in its step, then the recovery scanner re-issues the command after the timeout. Converges without an operator (`SagaFailureIT.fraudStoreOutage…`). |
| Permanent business conflicts (for example capture of released funds) | DLT + alert, then an operator investigates. |

## Operational consequences

- **DLT ownership:** the owning context's team (per the group in the topic name). Platform on-call owns the alert.
- **Runbook:** inspect by headers, then fix the cause, then replay (idempotent), or discard with justification.
- **DLT retention:** 7 days.
