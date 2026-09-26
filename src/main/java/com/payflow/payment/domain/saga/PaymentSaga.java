package com.payflow.payment.domain.saga;

import com.payflow.payment.domain.PaymentId;
import com.payflow.shared.domain.Identifiers;
import com.payflow.shared.domain.InvalidStateTransitionException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * State machine of one payment's distributed workflow (orchestration). The Payment context owns it and
 * persists it in the same database transaction as the Payment aggregate and the outgoing commands.
 *
 * <p>Every reply handler returns {@code false} when the reply does not match the current step. That is how
 * duplicates with new event ids, late replies after a timeout, and replies overtaken by a cancellation are
 * absorbed. They are acknowledged and ignored instead of corrupting state or being retried forever. This is
 * <em>business-level</em> deduplication on top of the eventId inbox.
 */
public final class PaymentSaga {

    private final UUID sagaId;
    private final PaymentId paymentId;
    private final String correlationId;
    private final CheckoutContext checkout;
    private final Instant createdAt;
    private final long version;
    private SagaStep step;
    private CompensationReason compensationReason;
    private String outcomeReason;
    private int stepAttempts;
    private Instant stepStartedAt;
    private Instant updatedAt;

    private PaymentSaga(PaymentSagaSnapshot s) {
        this.sagaId = Objects.requireNonNull(s.sagaId(), "sagaId");
        this.paymentId = Objects.requireNonNull(s.paymentId(), "paymentId");
        this.step = Objects.requireNonNull(s.step(), "step");
        this.compensationReason = s.compensationReason();
        this.outcomeReason = s.outcomeReason();
        this.stepAttempts = s.stepAttempts();
        this.stepStartedAt = Objects.requireNonNull(s.stepStartedAt(), "stepStartedAt");
        this.correlationId = Objects.requireNonNull(s.correlationId(), "correlationId");
        this.checkout = s.checkout() == null ? CheckoutContext.NONE : s.checkout();
        this.version = s.version();
        this.createdAt = s.createdAt();
        this.updatedAt = s.updatedAt();
    }

    public static PaymentSaga start(PaymentId paymentId, String correlationId, CheckoutContext checkout, Instant now) {
        return new PaymentSaga(new PaymentSagaSnapshot(Identifiers.timeOrderedUuid(), paymentId, SagaStep.AWAITING_RISK,
                null, null, 0, now, correlationId, checkout, 0L, now, now));
    }

    public static PaymentSaga rehydrate(PaymentSagaSnapshot snapshot) {
        return new PaymentSaga(snapshot);
    }

    public PaymentSagaSnapshot snapshot() {
        return new PaymentSagaSnapshot(sagaId, paymentId, step, compensationReason, outcomeReason, stepAttempts,
                stepStartedAt, correlationId, checkout, version, createdAt, updatedAt);
    }

    // ------------------------------------------------------------------------------------------------ replies

    public boolean onRiskApproved(Instant now) {
        return advance(SagaStep.AWAITING_RISK, SagaStep.AWAITING_FUNDS, now);
    }

    public boolean onRiskDeclined(String reason, Instant now) {
        return finish(SagaStep.AWAITING_RISK, SagaStep.REJECTED, reason, now);
    }

    public boolean onFundsReserved(Instant now) {
        return advance(SagaStep.AWAITING_FUNDS, SagaStep.AWAITING_SETTLEMENT, now);
    }

    public boolean onFundsRejected(String reason, Instant now) {
        return finish(SagaStep.AWAITING_FUNDS, SagaStep.REJECTED, reason, now);
    }

    public boolean onSettlementCompleted(Instant now) {
        return advance(SagaStep.AWAITING_SETTLEMENT, SagaStep.AWAITING_CAPTURE, now);
    }

    /** The rail said no: compensate by releasing the held funds. */
    public boolean onSettlementDeclined(String reason, Instant now) {
        if (step != SagaStep.AWAITING_SETTLEMENT) {
            return false;
        }
        startCompensation(CompensationReason.SETTLEMENT_DECLINED, reason, now);
        return true;
    }

    public boolean onFundsCaptured(Instant now) {
        return finish(SagaStep.AWAITING_CAPTURE, SagaStep.COMPLETED, null, now);
    }

    /** Compensation confirmed: the terminal step depends on why we compensated. */
    public boolean onFundsReleased(Instant now) {
        if (step != SagaStep.COMPENSATING) {
            return false;
        }
        SagaStep terminal = switch (compensationReason) {
            case SETTLEMENT_DECLINED -> SagaStep.FAILED;
            case CANCELLED -> SagaStep.CANCELLED;
            case TIMEOUT -> SagaStep.REJECTED;
        };
        moveTo(terminal, now);
        return true;
    }

    // ------------------------------------------------------------------------------------------------ commands

    public enum CancelDecision { NO_COMPENSATION, RELEASE_FUNDS }

    /**
     * Cancellation is allowed only before settlement is requested. After that, money may already be moving and
     * the correct operation is a refund, not a cancel.
     */
    public CancelDecision cancel(Instant now) {
        switch (step) {
            case AWAITING_RISK -> {
                finish(SagaStep.AWAITING_RISK, SagaStep.CANCELLED, "CANCELLED_BY_PAYER", now);
                return CancelDecision.NO_COMPENSATION;
            }
            case AWAITING_FUNDS -> {
                // A reservation may already exist or still be in flight: release it (idempotent, tombstone-safe).
                startCompensation(CompensationReason.CANCELLED, "CANCELLED_BY_PAYER", now);
                return CancelDecision.RELEASE_FUNDS;
            }
            default -> throw new InvalidStateTransitionException("PAYMENT_NOT_CANCELLABLE",
                    "Payment " + paymentId + " can no longer be cancelled (saga step " + step + ")");
        }
    }

    // ------------------------------------------------------------------------------------------------ recovery

    public enum TimeoutDecision { REJECT_PAYMENT, REJECT_AND_RELEASE_FUNDS, ESCALATE_TO_MANUAL_REVIEW }

    public boolean isOverdue(Instant now, java.time.Duration stepTimeout) {
        return step.isInFlight() && stepStartedAt.plus(stepTimeout).isBefore(now);
    }

    public boolean retriesExhausted(int maxStepAttempts) {
        return stepAttempts >= maxStepAttempts;
    }

    /** Re-issue the current step's command: counts the attempt and restarts the step timer. */
    public void recordRetry(Instant now) {
        stepAttempts++;
        stepStartedAt = now;
        updatedAt = now;
    }

    /**
     * Retries exhausted. Only compensate where the outcome is known to be "nothing happened yet":
     * <ul>
     *   <li>AWAITING_RISK: no money involved, so reject.</li>
     *   <li>AWAITING_FUNDS: a reservation may exist, so reject and release (release is idempotent).</li>
     *   <li>AWAITING_SETTLEMENT / AWAITING_CAPTURE / COMPENSATING: the outcome is <b>unknown</b>. The rail may
     *       have moved the money, so releasing funds could double-spend. Escalate to a human.</li>
     * </ul>
     */
    public TimeoutDecision onRetriesExhausted(Instant now) {
        return switch (step) {
            case AWAITING_RISK -> {
                finish(SagaStep.AWAITING_RISK, SagaStep.REJECTED, "RISK_ASSESSMENT_TIMEOUT", now);
                yield TimeoutDecision.REJECT_PAYMENT;
            }
            case AWAITING_FUNDS -> {
                startCompensation(CompensationReason.TIMEOUT, "FUNDS_RESERVATION_TIMEOUT", now);
                yield TimeoutDecision.REJECT_AND_RELEASE_FUNDS;
            }
            case AWAITING_SETTLEMENT, AWAITING_CAPTURE, COMPENSATING -> {
                outcomeReason = "TIMEOUT_IN_" + step;
                moveTo(SagaStep.MANUAL_REVIEW, now);
                yield TimeoutDecision.ESCALATE_TO_MANUAL_REVIEW;
            }
            default -> throw new InvalidStateTransitionException("SAGA_NOT_IN_FLIGHT", "Saga " + sagaId + " is " + step);
        };
    }

    // ------------------------------------------------------------------------------------------------ internals

    private boolean advance(SagaStep expected, SagaStep next, Instant now) {
        if (step != expected) {
            return false;
        }
        moveTo(next, now);
        return true;
    }

    private boolean finish(SagaStep expected, SagaStep terminal, String reason, Instant now) {
        if (step != expected) {
            return false;
        }
        outcomeReason = reason;
        moveTo(terminal, now);
        return true;
    }

    private void startCompensation(CompensationReason reason, String outcome, Instant now) {
        compensationReason = reason;
        outcomeReason = outcome;
        moveTo(SagaStep.COMPENSATING, now);
    }

    private void moveTo(SagaStep next, Instant now) {
        step = next;
        stepAttempts = 0;
        stepStartedAt = now;
        updatedAt = now;
    }

    public UUID sagaId() {
        return sagaId;
    }

    public PaymentId paymentId() {
        return paymentId;
    }

    public SagaStep step() {
        return step;
    }

    public CompensationReason compensationReason() {
        return compensationReason;
    }

    public String outcomeReason() {
        return outcomeReason;
    }

    public int stepAttempts() {
        return stepAttempts;
    }

    public Instant stepStartedAt() {
        return stepStartedAt;
    }

    public String correlationId() {
        return correlationId;
    }

    public CheckoutContext checkout() {
        return checkout;
    }

    public long version() {
        return version;
    }
}
