# Event Contracts

## Envelope (`contracts/Envelope.v1.schema.json`)

```json
{
  "eventId": "01a0df0a-d1e8-7cc3-ab70-1124dc1f9999",
  "eventType": "FundsCaptured",
  "eventVersion": 1,
  "producer": "account-service",
  "aggregateType": "Payment",
  "aggregateId": "01a0df0a-96ea-76bf-aa69-f20da1260c87",
  "occurredAt": "2026-09-26T18:47:03.412Z",
  "correlationId": "14cb9fcd-b44d-4789-9282-e799922e1e30",
  "causationId": "01a0df0a-c2b1-7a55-9d10-5f1f6a2b3c4d",
  "sagaId": "01a0df0a-96ec-76ae-a722-beeb2be5c754",
  "payload": { "paymentId": "01a0df0a-…", "payerAccountId": "…", "payeeAccountId": "…", "amount": "50.00", "currency": "USD" }
}
```

- The Kafka record **key** is `aggregateId`, and consumers reject a mismatch.
- **Headers:** `payflow-event-id`, `payflow-event-type`, `payflow-event-version`, `payflow-producer`, `payflow-correlation-id`, `traceparent`.
- `traceparent` is the W3C context captured when the outbox row was written, so the trace continues across the asynchronous hop.

## Catalog

The single source of truth is `com.payflow.contracts.EventCatalog`, with schemas in `src/main/resources/contracts/<Type>.v<N>.schema.json`.

| Type | Version(s) | Topic | Owner | Payload |
|---|---|---|---|---|
| PaymentCreated | 1 | payment.events | payment-service | paymentId, payerAccountId, payeeAccountId, amount, currency, method |
| PaymentAuthorized / PaymentProcessingStarted / PaymentCancelled | 1 | payment.events | payment-service | paymentId |
| PaymentRejected / PaymentFailed | 1 | payment.events | payment-service | paymentId, reason |
| PaymentSettled | 1 | payment.events | payment-service | paymentId, amount, currency |
| AssessPaymentRisk | 1 | fraud.commands | payment-service | paymentId, payer, payee, amount, currency, method, [deviceId, ipAddress, userAgent, countryCode] |
| RiskAssessed | **1, 2** | fraud.events | fraud-service | v1: paymentId, approved, riskScore, [reason]; v2 adds [signalCodes, modelVersion] |
| ReserveFunds | 1 | funds.commands | payment-service | paymentId, payer, payee, amount, currency |
| CaptureFunds | 1 | funds.commands | payment-service | paymentId |
| ReleaseFunds | 1 | funds.commands | payment-service | paymentId, payer, amount, currency, reason |
| FundsReserved | 1 | funds.events | account-service | paymentId, reservationId, payer, amount, currency |
| FundsReservationFailed | 1 | funds.events | account-service | paymentId, reason |
| FundsCaptured | 1 | funds.events | account-service | paymentId, payer, payee, amount, currency |
| FundsReleased | 1 | funds.events | account-service | paymentId, amount, currency, reason |
| FundsDeposited | 1 | funds.events | account-service | depositId, accountId, amount, currency (key = accountId) |
| SubmitSettlement | 1 | settlement.commands | payment-service | paymentId, method, amount, currency, [reference] |
| SettlementCompleted | 1 | settlement.events | settlement-service | paymentId, settlementId, providerReference |
| SettlementDeclined | 1 | settlement.events | settlement-service | paymentId, settlementId, reason |

## Rules

1. **Events are contracts, not internal models.** They are never JPA entities (ArchUnit) and never domain events. The outbound adapters map internal state to these records (an anti-corruption layer).
2. **Money is always a decimal string** plus an ISO currency.
3. **Commands carry the state the participant needs** (event-carried state transfer). Participants never call back into Payment.
4. **Data minimisation.** `RiskAssessed` carries signal codes, never the raw evidence (IP, device). Personal data appears only on `fraud.commands`, which only Fraud may read.
5. **Producers are strict** (`additionalProperties:false`, validated in the build) and **consumers are tolerant** (unknown fields ignored). Unknown types, unsupported versions, the wrong producer or the wrong topic are rejected to the DLT.
6. **Evolution:** additive only within a type; breaking changes get a new type (ADR-016).
