# ADR-011: JSON envelope + JSON Schema contracts as code; Schema Registry deferred with explicit triggers

- Status: Accepted (WP-02)
- Date: 2026-09-27

## Context

Events are integration contracts between contexts that will become independently deployed services. We need:
- versioned, validated contracts
- compatibility checking
- debuggability
- no leakage of JPA or domain types

## Decision

**Wire format.**
- A JSON **envelope** (`eventId, eventType, eventVersion, producer, aggregateType, aggregateId, occurredAt, correlationId, causationId, sagaId, payload`).
- Kafka headers duplicate the routing fields (type, version, producer, correlation, `traceparent`) so they can be read without parsing the payload.

**Payloads.**
- Plain Java records in `com.payflow.contracts` (the published language, JDK-only, ArchUnit enforced), with mandatory fields checked in constructors.
- Amounts are decimal strings.

**Contracts as code.**
- One JSON Schema (draft 2020-12) per type and version in `src/main/resources/contracts/`, with owner topic and producer.
- `EventContractTest` enforces the following in every build:
  1. Every catalog version has a schema, and its topic and owner match.
  2. Producer output validates **strictly** (`additionalProperties:false`), so a silently changed record fails the build.
  3. Successive versions are backward compatible (no removed field, no type change, no newly required field).
  4. Upcasting and tolerant reading work.

**Schema Registry: evaluated, not deployed in WP-02.**
- All producers and consumers are in one repository and one build, so the build is the compatibility gate.
- A registry adds a stateful runtime dependency on the hot path (serializer lookups) for a guarantee we already get.
- **Triggers to introduce it** (Confluent-compatible, for example Apicurio):
  - The first context is extracted into its own repository or deployable, or
  - A consumer outside PayFlow subscribes.

  Compatibility mode will be BACKWARD_TRANSITIVE, the same rules as the test.

## Alternatives

| Format | For | Against |
|---|---|---|
| **JSON + JSON Schema (chosen)** | Human-readable on the wire and in the DLT, jq-able, no code generation, native in Jackson | Larger messages; weaker typing than Avro or Protobuf; schema enforcement needs our tests (or a registry) |
| Avro + registry | Compact, strong schema-evolution tooling, the Kafka ecosystem default | Code generation or GenericRecord ergonomics; the registry is mandatory at runtime; opaque bytes in the DLT |
| Protobuf | Compact, excellent evolution rules (field numbers), polyglot | Code generation; less natural with Jackson; needs a registry for Kafka use |

## Trade-offs

We gain: debuggability, zero new infrastructure, strict build-time contract governance.

We accept:
- About 2–3× larger payloads than Avro. Negligible at current volume; to be re-evaluated at WP-03.
- No runtime schema-ID resolution until a registry exists.

## Failure implications

- Unparseable → `DESERIALIZATION` → DLT.
- Contract violation → `CONTRACT_VIOLATION` → DLT.
- Unknown or too-new version → `UNSUPPORTED_VERSION` → DLT.

None of these is retried (ConsumerSemanticsIT).

## Operational consequences

- Adding a field means adding a new schema version and passing the compatibility test.
- The DLT is readable by humans, which matters when investigating financial events.
