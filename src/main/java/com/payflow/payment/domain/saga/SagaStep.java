package com.payflow.payment.domain.saga;

/**
 * Steps of the orchestrated payment saga.
 *
 * <pre>
 *  AWAITING_RISK ──approved──▶ AWAITING_FUNDS ──reserved──▶ AWAITING_SETTLEMENT ──completed──▶ AWAITING_CAPTURE ──captured──▶ COMPLETED
 *     │ declined                 │ rejected                    │ declined                          (timeout: MANUAL_REVIEW)
 *     ▼                          ▼                             ▼
 *   REJECTED                  REJECTED                    COMPENSATING ──funds released──▶ FAILED / CANCELLED / REJECTED
 *
 *  cancel: AWAITING_RISK → CANCELLED;  AWAITING_FUNDS → COMPENSATING(CANCELLED)
 *  step timeout: re-issue command (bounded); then AWAITING_RISK → REJECTED, AWAITING_FUNDS → COMPENSATING(TIMEOUT),
 *                AWAITING_SETTLEMENT / AWAITING_CAPTURE / COMPENSATING → MANUAL_REVIEW (outcome unknown, never auto-compensated)
 * </pre>
 */
public enum SagaStep {
    AWAITING_RISK,
    AWAITING_FUNDS,
    AWAITING_SETTLEMENT,
    AWAITING_CAPTURE,
    COMPENSATING,
    COMPLETED,
    REJECTED,
    FAILED,
    CANCELLED,
    MANUAL_REVIEW;

    /** The workflow has finished; MANUAL_REVIEW is deliberately not terminal (a human still has to decide). */
    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == FAILED || this == CANCELLED;
    }

    public boolean isInFlight() {
        return this == AWAITING_RISK || this == AWAITING_FUNDS || this == AWAITING_SETTLEMENT
                || this == AWAITING_CAPTURE || this == COMPENSATING;
    }
}
