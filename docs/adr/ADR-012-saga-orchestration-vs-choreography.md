# ADR-012: Orchestrated saga for the payment workflow; choreography for followers

- Status: Accepted (WP-02)
- Date: 2026-09-27

## Context

A payment spans Fraud (MongoDB), Account/funds, Settlement (an external rail) and Ledger. Each step commits locally. Failures require **compensation** (release held funds), not rollback. Timeouts require **recovery**.

## Decision

- **Orchestration.** `PaymentSagaService` in the Payment context owns an explicit, persisted state machine (`payment.payment_saga`, one per payment) and issues commands:

  ```
  AssessPaymentRisk → ReserveFunds → SubmitSettlement → CaptureFunds
  ```

  The compensation is `ReleaseFunds`. Each reply is handled in one local transaction:
  1. update the saga (versioned)
  2. update the payment
  3. write the next command and the lifecycle events to the outbox
- **Choreography where no coordination is needed.** Ledger is a follower that posts journals from `funds.events` (`FundsCaptured`, `FundsDeposited`). It is not a saga step and cannot fail the payment.

## Alternatives

| | Choreography (each service reacts to events) | Orchestration (chosen) |
|---|---|---|
| Where the flow lives | Implicitly across N services | In one state machine you can read, test and query |
| Compensation | Each service must infer when to undo | The orchestrator decides centrally, with its reason recorded |
| Timeouts and stuck detection | Needs a separate process-tracking component anyway | Built in: the saga row has step + started_at + attempts |
| Coupling | Low, but cyclic event dependencies are hard to see | The orchestrator knows participants' command contracts |
| "Where is my payment?" | Hard | `GET /payments/{id}/saga` |

For money movement with compensation and "outcome unknown" states, explicit orchestration is the defensible choice. Choreography is kept for the Ledger follower, where it is simpler and safe.

**Saga vs 2PC.** A saga gives up isolation (other readers can see intermediate states such as reserved funds) in exchange for availability and autonomy. That is acceptable because intermediate states are modelled explicitly (RESERVED is a real, visible business state).

## Trade-offs

We gain:
- An explicit workflow.
- Central compensation and recovery.
- One place to debug.

We accept:
- The orchestrator is a central dependency of the flow, but not of the participants, which do not call back.
- Eventual consistency and visible intermediate states.

## Failure implications (see SAGA-DESIGN.md for the full table)

- Every step is resumable and idempotent. Stale or duplicate replies are ignored by step guards.
- Compensation happens only when the outcome is known. An unknown settlement outcome goes to **MANUAL_REVIEW** and is never auto-released, because the money may have moved.

## Operational consequences

- Monitor saga step age (the recovery scanner and its metrics).
- MANUAL_REVIEW needs an operator runbook: query the provider by idempotency key = paymentId, then capture or release.
