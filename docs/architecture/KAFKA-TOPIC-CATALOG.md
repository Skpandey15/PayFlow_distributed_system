# Kafka Topic Catalog

Defaults for all main topics: 6 partitions (test: 3), replication factor 3 in production (local: 1), `min.insync.replicas=2` in production, retention 7 days, `cleanup.policy=delete`. Topics are declared by the application (`KafkaMessagingConfiguration`), and broker auto-creation is disabled in compose.

| Topic | Purpose | Producer (sole writer) | Consumers (group) | Key | Ordering requirement | Replay | Schema compatibility |
|---|---|---|---|---|---|---|---|
| `payment.events` | Public payment lifecycle (Created, Authorized, ProcessingStarted, Rejected, Cancelled, Settled, Failed) | payment-service (outbox) | none internal. It is the integration stream for notifications, analytics and merchants. Tests read it. | paymentId | Per payment (verified by `OutboxIT.lifecycleEvents…`) | Yes: rebuild read models / webhooks | Additive only (ADR-016) |
| `fraud.commands` | AssessPaymentRisk (includes checkout evidence = **personal data**) | payment-service (outbox) | fraud-service | paymentId | Per payment | Yes (the stored decision is re-announced; never re-scored) | Additive only |
| `fraud.events` | RiskAssessed v1/v2 | fraud-service (direct, consume-process-produce) | payment-service | paymentId | Per payment | Yes (saga step guard) | v1→v2 additive, upcast |
| `funds.commands` | ReserveFunds, CaptureFunds, ReleaseFunds | payment-service (outbox) | account-service | paymentId | **Strict** per payment (reserve before capture or release) | Yes (inbox + unique reservation + tombstone) | Additive only |
| `funds.events` | FundsReserved, FundsReservationFailed, FundsCaptured, FundsReleased, FundsDeposited | account-service (outbox) | payment-service (saga), ledger-service (follower) | paymentId (FundsDeposited: accountId) | Per payment / per account | Yes (inbox + journal reference) | Additive only |
| `settlement.commands` | SubmitSettlement | payment-service (outbox) | settlement-service | paymentId | Per payment | Yes (resumable, provider idempotency key) | Additive only |
| `settlement.events` | SettlementCompleted, SettlementDeclined | settlement-service (outbox) | payment-service | paymentId | Per payment | Yes (saga step guard) | Additive only |

## Retry and dead-letter topics (per consumer group)

Created automatically by `@RetryableTopic`:

- `<topic>-<group>-retry-0|1|2`: delays 1 s / 3 s / 9 s (max 30 s)
- `<topic>-<group>-dlt`

Examples: `funds.commands-account-service-dlt`, `funds.events-ledger-service-dlt`, `fraud.events-payment-service-retry-0`.

They are per group because one group's failure must not be redelivered to other groups (a shared retry topic would do that). DLT retention is 7 days, and the owner is the consumer's team.

## Why this granularity

- **Per context, commands vs events.** ACLs follow ownership (one writer per topic). Readers see only what they need, so Ledger never sees fraud evidence.
- **Not per event type.** A payment's reserve and release must share a partition to stay ordered.
- **Not one workflow topic.** It would force every service to read everything (least-privilege and PII violation) and create one blast radius.

## Security / ACL expectation (see deploy/kafka/acl-matrix.sh)

| Principal | Write | Read |
|---|---|---|
| payment-service | payment.events, fraud.commands, funds.commands, settlement.commands | fraud.events, funds.events, settlement.events |
| fraud-service | fraud.events | fraud.commands |
| account-service | funds.events | funds.commands |
| settlement-service | settlement.events | settlement.commands |
| ledger-service | none | funds.events |

Each principal also needs prefixed read/write on its own `<topic>-<group>-*` retry and DLT topics.

## Partition count assumption

6 partitions set the ceiling of 6 parallel consumers per group. That is sufficient for the lab and the first production phase. Revisit it with the WP-03 load test. Increasing partitions remaps keys, so it is done only with a drain (ADR-008).
