# ADR-022: Manual review is resolved by resuming the workflow with evidence, never by setting an outcome

- Status: Accepted (WP-03, closes WP-02 K3)
- Date: 2026-09-27

## Context

WP-02 safely escalates sagas whose outcome is **unknown** (settlement, capture or compensation timed out after
all retries) to MANUAL_REVIEW, and keeps the funds reserved. No tooling existed to resolve these cases. The
tempting shortcuts are dangerous:

| Shortcut | Why it is dangerous |
|---|---|
| "Mark settled" | Money may not have moved |
| "Mark failed and release" | Money may have moved: a double spend |

## Decision

1. An authenticated ops API at `/api/v1/ops/manual-reviews`:

   | Requirement | How |
   |---|---|
   | Authorisation | Scope `ops:manual-review` (separate from `payments:admin`: least privilege), checked by the route rule **and** the use case |
   | Rate limit | 10 decisions per minute per operator |
   | Endpoints | `GET /` (queue, oldest first); `GET /{paymentId}` (payment, our settlement record including unanswered attempts with NOT_SENT vs UNKNOWN, a **live rail inquiry**, allowed decisions, decision history) |
   | Decisions | `POST /{paymentId}/decisions` with `Idempotency-Key` and a mandatory reason and ticket reference |

2. **Two decisions only.** Neither sets a financial outcome.

   | Decision | Allowed when | What it does |
   |---|---|---|
   | `RESUME` | always, within the rail's idempotency window (24 h, else 409 `RAIL_IDEMPOTENCY_WINDOW_EXPIRED`) | saga MANUAL_REVIEW → the escalated step; re-issue that step's command |
   | `CONFIRM_NOT_SETTLED` | escalated from AWAITING_SETTLEMENT **and** the rail does not report ACCEPTED | void the key at the rail, then RESUME. The re-issued submission meets the voided key; the rail declines it; the existing SettlementDeclined path compensates (releases funds). |

   RESUME is safe because every participant is idempotent per paymentId, so the true outcome is re-established by
   the participants, not by the operator. If the rail says ACCEPTED, CONFIRM_NOT_SETTLED is refused with 409
   `RAIL_REPORTS_SETTLED`: the evidence contradicts the operator.
3. **There is no "force success" or "force fail" endpoint**, by design.
4. **Audit:**
   - `payment.manual_review_decision` is append-only: the runtime DB role has only INSERT and SELECT.
   - Each record holds operator, decision, reason, ticket, frozen evidence (rail state, attempts, error codes),
     correlation id and time.
   - The operator audit log line (`payflow.ops.audit`) and `payflow.manual_review.decisions{decision}` complement it.
5. **Concurrency:** the saga's optimistic version makes two operators' decisions mutually exclusive (409 for the
   loser). Idempotency-Key replays return the original decision.

## Alternatives

| Alternative | Why not |
|---|---|
| A generic "set payment status" admin endpoint | The classic source of irreversible financial incidents. Rejected. |
| Auto-resolve from the inquiry | Tempting for ACCEPTED (RESUME is safe), not for NOT_FOUND: "not found" can mean "still in transit". Voiding first makes that safe too. Left to humans in WP-03 because the rail's semantics must be confirmed per provider. Candidate for automation later. |
| Four-eyes approval | Right for high amounts. Deferred (needs a second-approver model); recorded in the review. |

## Trade-offs and failure implications

**The void-then-resume ordering is crash-safe:**

| Crash point | Effect |
|---|---|
| After the void, before the transaction | Nothing changed locally; the operator retries (the void is idempotent). |
| After the transaction | The command is in the outbox and will be delivered. |

The inquiry and void go through a **separate bulkhead** (2 per rail) and the same circuit breaker. An operator
investigation cannot take the payment traffic's share of the provider, and a failing rail makes the inquiry
report UNREACHABLE instead of hanging.

## Operational consequences

- Alerts: `ManualReviewWaiting` (ticket after 15 min), `ManualReviewAging` (page at 4 h: customer funds held).
- Runbook: RUNBOOKS.md#manual-review.
- Evidence: `ManualReviewIT` covers both paths:
  - Never received → CONFIRM_NOT_SETTLED → FAILED, funds released, rail shows the key voided.
  - Rail settled but the answer was lost → CONFIRM refused → RESUME → SETTLED.
