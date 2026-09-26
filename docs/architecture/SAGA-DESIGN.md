# Payment Saga Design (orchestration)

## State machine (`PaymentSaga`, persisted in `payment.payment_saga`)

```
AWAITING_RISK ──RiskAssessed(approve)──▶ AWAITING_FUNDS ──FundsReserved──▶ AWAITING_SETTLEMENT ──SettlementCompleted──▶ AWAITING_CAPTURE ──FundsCaptured──▶ COMPLETED
   │ RiskAssessed(decline)                  │ FundsReservationFailed          │ SettlementDeclined
   ▼                                        ▼                                 ▼
REJECTED                                 REJECTED                       COMPENSATING ──FundsReleased──▶ FAILED | CANCELLED | REJECTED
                                                                          ▲
        cancel (AWAITING_FUNDS) ────────────────────────────────────────── ┘       (terminal depends on compensation reason)
        cancel (AWAITING_RISK) → CANCELLED
```

| Step | Command issued on entry | Payment state | Reply that advances | Timeout |
|---|---|---|---|---|
| AWAITING_RISK | AssessPaymentRisk | CREATED | RiskAssessed | 30 s |
| AWAITING_FUNDS | ReserveFunds | CREATED | FundsReserved / FundsReservationFailed | 30 s |
| AWAITING_SETTLEMENT | SubmitSettlement | AUTHORIZED → PROCESSING | SettlementCompleted / Declined | 2 min |
| AWAITING_CAPTURE | CaptureFunds | PROCESSING | FundsCaptured | 30 s |
| COMPENSATING | ReleaseFunds | REJECTED / FAILED / CANCELLED | FundsReleased | 30 s |

## Transaction per reply

```
BEGIN
  INSERT payment.processed_event (consumer, eventId)   -- inbox; duplicate → skip everything
  SELECT saga, payment
  apply transition (stale if the reply does not match the step → no writes)
  UPDATE saga (version check)  ;  UPDATE payment (version check)
  INSERT outbox: next command + payment lifecycle events
COMMIT  →  offset committed
```

## Compensation

Compensation is **not** a rollback. The reservation committed and was visible. Compensation is a new, forward business action, `ReleaseFunds`, that restores `available`. It is:
- **idempotent**: release of RELEASED/REJECTED is a no-op
- **order-safe**: a release that overtakes its reserve leaves a RELEASED tombstone, and the late reserve is refused
- **never applied to captured funds**: that would require a refund, so it goes to DLT + alert

| Trigger | Compensation | Terminal |
|---|---|---|
| SettlementDeclined | ReleaseFunds(SETTLEMENT_DECLINED) | FAILED |
| Cancel in AWAITING_FUNDS | ReleaseFunds(CANCELLED) | CANCELLED |
| Funds step timed out (retries exhausted) | ReleaseFunds(TIMEOUT), payment REJECTED | REJECTED |

**When compensation fails:**
- A transient failure retries (bounded retry topics), then goes to the DLT, and the saga stays COMPENSATING.
- The recovery scanner re-issues ReleaseFunds.
- After the retry budget, the saga goes to MANUAL_REVIEW with an ERROR log and a metric.
- Funds remain held (a safe state) until an operator acts.

## Recovery (`SagaRecoveryService`, every 10 s on every replica)

```
SELECT in-flight sagas whose step_started_at < now − shortest timeout  FOR UPDATE SKIP LOCKED LIMIT 50
for each overdue saga:
    retries left   → re-issue the step's command (idempotent at the participant), attempts+1, restart timer
    exhausted      → AWAITING_RISK: reject payment
                     AWAITING_FUNDS: reject payment + ReleaseFunds (compensate)
                     AWAITING_SETTLEMENT / AWAITING_CAPTURE / COMPENSATING: MANUAL_REVIEW (outcome unknown)
```

**Why unknown settlement outcomes are never auto-compensated.** A timeout on the rail does not mean "nothing happened". The rail may have moved the money. Releasing the hold would let the payer spend money that is already gone, which is a double spend. The saga escalates and keeps the hold. The operator queries the provider with idempotency key = paymentId, then either re-issues (completes) or confirms the decline. This is verified in `SagaFailureIT.unknownSettlementOutcomeIsEscalatedNotCompensated`.

Recovery is:
- **idempotent**: re-issued commands hit natural keys
- **safe under replicas**: SKIP LOCKED plus the saga version
- **observable**: `payflow.saga.recovery{action,step}`, WARN/ERROR logs with sagaId and correlationId

## Evidence

| Scenario | Test |
|---|---|
| Happy path, balances and ledger | `PaymentApiIT.sagaSettles…` |
| Insufficient funds | `PaymentApiIT.insufficientFunds…` |
| Risk decline before any hold | `PaymentApiIT.highRiskPayment…` |
| Settlement decline → compensation | `SagaFailureIT.settlementDeclineReleasesHeldFunds`, `PaymentApiIT.settlementDecline…` |
| Cancel racing a reservation | `SagaFailureIT.cancelWhileReservationIsInFlight…` (late FundsReserved is STALE) |
| Check-then-act gap (payee frozen after create) | `SagaFailureIT.accountFrozenAfterCreation…` |
| Fraud store outage → DLT → recovery | `SagaFailureIT.fraudStoreOutage…` (MongoDB container paused) |
| PostgreSQL stall mid-workflow | `SagaFailureIT.postgresOutage…` (PostgreSQL container paused) |
| Timeout compensation | `SagaFailureIT.fundsReservationTimeout…` |
| Unknown outcome → manual review | `SagaFailureIT.unknownSettlementOutcome…` |
| State machine rules | `PaymentSagaTest`; orchestrator ordering: `PaymentSagaServiceTest` |
