# ADR-016: Event schema evolution: additive versions, upcasting consumers, tolerant readers

- Status: Accepted (WP-02)
- Date: 2026-09-27

## Context

Producers and consumers deploy independently (they will once extracted), and old events stay on the log for the retention period and can be replayed. A schema change must not break consumers that are either behind or ahead.

## Decision

- **Versions are explicit.** `eventVersion` travels in the envelope and in a header. Each version has its own payload record and JSON Schema. A schema is never mutated in place.
- **Allowed change within a type:** add an **optional** field, by creating a new version. **Breaking changes** (remove or rename a field, change a type, add a required field, change semantics) need a **new event type**, or a new topic in extreme cases, with a dual-publish migration.
- **Consumers:**
  - read any supported version and **upcast** it to the current one (`EventCatalog` upcasters, for example `RiskAssessedV2.fromV1`)
  - ignore unknown fields (tolerant reader)
  - reject unsupported versions to the DLT
- **Producers** emit the current version only.
- **Deployment order:**
  1. Ship consumers that understand v(n+1).
  2. Then switch producers.
  3. Retire v(n) readers after retention plus the replay window.

## Compatibility terminology as applied here

| Compatibility | Meaning here |
|---|---|
| Backward | The new consumer reads old data. Achieved by upcasting and defaults. |
| Forward | The old consumer reads new data. Achieved because additions are optional and unknown fields are ignored. |
| Full | Both. This is what the additive-only rule gives us. The build enforces it (`EventContractTest.successiveVersionsAreBackwardCompatible`, with a negative test proving the checker catches removals, type changes and new required fields). |
| Registry mode (when introduced, ADR-011) | BACKWARD_TRANSITIVE |

## Worked example (implemented)

`RiskAssessed`:
- v1: `paymentId, approved, riskScore, reason`
- v2 (additive): `+ signalCodes, + modelVersion` (both optional)

Fraud emits v2. The payment saga consumes v2 and upcasts v1 (tested). A v1-only reader accepts v2 payloads (tested).

## Alternatives

| Option | Why not chosen |
|---|---|
| Version in the topic name for every change | Topic sprawl, and consumers must re-subscribe. Reserved for breaking changes. |
| No versions ("just be careful") | Silent breakage, caught only in production. |
| Consumer-driven contract tests only (Pact) | Valuable across repositories. Within one repository the schema tests are stronger and cheaper. |

## Failure implications

- An unknown version is a permanent failure: DLT, then fix, then replay.
- An unsupported version cannot poison a partition, because it is non-blocking.

## Operational consequences

- Every contract change requires a schema file, a catalog entry and an upcaster.
- The compatibility test must pass before release.
