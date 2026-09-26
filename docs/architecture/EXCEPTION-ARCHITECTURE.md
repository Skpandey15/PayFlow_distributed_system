# Exception Architecture (synchronous + asynchronous)

## Taxonomy

| Layer | Type | Meaning |
|---|---|---|
| Domain | `DomainRuleViolationException` | business invariant violated (e.g. FUNDS_INSUFFICIENT, PAYMENT_SAME_ACCOUNT) |
| Domain | `InvalidStateTransitionException` | lifecycle does not allow the action (e.g. PAYMENT_NOT_CANCELLABLE, FUNDS_ALREADY_CAPTURED) |
| Application | `NotFoundException` / `ForbiddenException` / `UnprocessableException` / `ConflictException` | request-level outcome categories |
| Application | `ConcurrencyConflictException` | optimistic version check failed |
| Application | `DependencyUnavailableException` (e.g. `FraudStoreUnavailableException`, `GatewayUnavailableException`) | a dependency is down; **transient infrastructure**, translated at the adapter boundary |
| Contracts | `ContractViolation` | payload misses a mandatory field |
| Messaging | `EventDeserializationException` | poison message (not an envelope) |
| Messaging | `InvalidEventException` | contract violation (unknown type, key mismatch, bad payload) |
| Messaging | `UnsupportedEventVersionException` | version this consumer cannot read |
| Messaging | `UntrustedEventException` | wrong producer or topic for the type (security) |
| Messaging | `PermanentEventProcessingException` / `TransientEventProcessingException` | the classified wrappers the retry topology routes on |
| (outcome, not exception) | DUPLICATE / STALE | redelivery or superseded reply, logged and counted, **not** an error |

Adapters translate technical exceptions into these types, so infrastructure exceptions never reach the domain. For example, MongoDB timeouts become `FraudStoreUnavailableException` and rail timeouts become `GatewayUnavailableException`.

## Asynchronous policy (`FailureClassifier`, enforced by `FailureClassifierTest`)

| Category | Examples | Retry? | DLT? | Compensate? | Alert? | Fail fast? |
|---|---|---|---|---|---|---|
| DESERIALIZATION | not JSON, not an envelope | no | yes, immediately | no | yes (DLT alert) | yes |
| CONTRACT_VIOLATION | missing field, unknown type, key ≠ aggregate | no | yes | no | yes | yes |
| UNSUPPORTED_VERSION | eventVersion 99 | no | yes | no | yes | yes |
| UNTRUSTED_SOURCE | forged producer, wrong topic | no | yes | no | **yes (security)** | yes |
| BUSINESS_RULE | capture of released funds, reply for unknown saga | no | yes | if the saga can: via its own logic | yes | yes |
| DATA_INTEGRITY | unexpected constraint violation | no | yes | no | yes | yes |
| CONCURRENCY | optimistic lock, deadlock | yes (bounded) | after exhaustion | no | no (metric) | no |
| TRANSIENT_INFRASTRUCTURE | DB/Mongo/Kafka/rail unavailable, timeouts | yes (1 s, 3 s, 9 s) | after exhaustion | via saga recovery if a step times out | on DLT | no |
| UNKNOWN | unclassified bug | yes (bounded) | after exhaustion | no | on DLT (with stack trace in the log only) | no |

Business *outcomes* are **not** exceptions: insufficient funds, a risk decline, or a rail decline become events (`FundsReservationFailed`, `RiskAssessed(approved=false)`, `SettlementDeclined`), and the saga reacts to them.

## Synchronous policy (unchanged from WP-01, extended)

The HTTP boundary (`ApiExceptionHandler`) maps categories to RFC 9457 problems:

| Status | Cause |
|---|---|
| 400 | validation |
| 401 / 403 | security |
| 404 | not found or not visible |
| 409 | conflict / concurrency / invalid transition |
| 422 | business rule |
| 503 + Retry-After | dependency unavailable |
| 500 | unexpected (correlation id only) |

## Forbidden practices (and where they are prevented)

- **Catch-and-continue or swallow.** There are two catch sites: `EventConsumerSupport`, which classifies, logs once and **rethrows**, and the schedulers (relay, recovery), which log WARN and retry on the next run because their work is durable in the database.
- **Retry everything.** Permanent categories are excluded from retry topics.
- **Stack traces in events or APIs.** DLT stack-trace and message headers are stripped (`ConsumerSemanticsIT.poison…` asserts it). API 5xx responses carry only the correlation id.
- **Infrastructure exceptions in the domain.** Prevented by ArchUnit (the domain is framework-free) and by adapter translation.
