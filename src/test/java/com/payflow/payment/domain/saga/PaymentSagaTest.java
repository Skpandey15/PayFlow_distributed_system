package com.payflow.payment.domain.saga;

import com.payflow.payment.domain.PaymentId;
import com.payflow.shared.domain.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentSagaTest {

    final Instant t0 = Instant.parse("2026-09-01T10:00:00Z");

    PaymentSaga saga() {
        return PaymentSaga.start(PaymentId.newId(), "corr", CheckoutContext.NONE, t0);
    }

    @Test
    void happyPathVisitsEveryStepInOrder() {
        PaymentSaga s = saga();
        assertThat(s.step()).isEqualTo(SagaStep.AWAITING_RISK);
        assertThat(s.onRiskApproved(t0)).isTrue();
        assertThat(s.onFundsReserved(t0)).isTrue();
        assertThat(s.onSettlementCompleted(t0)).isTrue();
        assertThat(s.onFundsCaptured(t0)).isTrue();
        assertThat(s.step()).isEqualTo(SagaStep.COMPLETED);
    }

    @Test
    void repliesThatDoNotMatchTheCurrentStepAreStaleAndChangeNothing() {
        PaymentSaga s = saga();
        s.onRiskApproved(t0);
        assertThat(s.onRiskApproved(t0)).as("duplicate reply with a new eventId").isFalse();
        assertThat(s.onFundsCaptured(t0)).as("reply for a future step").isFalse();
        assertThat(s.onSettlementDeclined("x", t0)).isFalse();
        assertThat(s.step()).isEqualTo(SagaStep.AWAITING_FUNDS);
    }

    @Test
    void settlementDeclineCompensatesAndEndsFailed() {
        PaymentSaga s = saga();
        s.onRiskApproved(t0);
        s.onFundsReserved(t0);
        assertThat(s.onSettlementDeclined("SIMULATED_DECLINE", t0)).isTrue();
        assertThat(s.step()).isEqualTo(SagaStep.COMPENSATING);
        assertThat(s.compensationReason()).isEqualTo(CompensationReason.SETTLEMENT_DECLINED);
        assertThat(s.onFundsReleased(t0)).isTrue();
        assertThat(s.step()).isEqualTo(SagaStep.FAILED);
        assertThat(s.onFundsReleased(t0)).as("duplicate compensation confirmation").isFalse();
    }

    @Test
    void cancellationIsOnlyPossibleBeforeSettlementIsRequested() {
        PaymentSaga early = saga();
        assertThat(early.cancel(t0)).isEqualTo(PaymentSaga.CancelDecision.NO_COMPENSATION);
        assertThat(early.step()).isEqualTo(SagaStep.CANCELLED);

        PaymentSaga holding = saga();
        holding.onRiskApproved(t0);
        assertThat(holding.cancel(t0)).isEqualTo(PaymentSaga.CancelDecision.RELEASE_FUNDS);
        assertThat(holding.onFundsReserved(t0)).as("late reservation after cancel is stale").isFalse();
        holding.onFundsReleased(t0);
        assertThat(holding.step()).isEqualTo(SagaStep.CANCELLED);

        PaymentSaga settling = saga();
        settling.onRiskApproved(t0);
        settling.onFundsReserved(t0);
        assertThatThrownBy(() -> settling.cancel(t0)).isInstanceOf(InvalidStateTransitionException.class)
                .extracting("code").isEqualTo("PAYMENT_NOT_CANCELLABLE");
    }

    @Test
    void timeoutPolicyOnlyCompensatesWhereTheOutcomeIsKnown() {
        PaymentSaga risk = saga();
        assertThat(risk.onRetriesExhausted(t0)).isEqualTo(PaymentSaga.TimeoutDecision.REJECT_PAYMENT);

        PaymentSaga funds = saga();
        funds.onRiskApproved(t0);
        assertThat(funds.onRetriesExhausted(t0)).isEqualTo(PaymentSaga.TimeoutDecision.REJECT_AND_RELEASE_FUNDS);
        assertThat(funds.step()).isEqualTo(SagaStep.COMPENSATING);

        PaymentSaga settlement = saga();
        settlement.onRiskApproved(t0);
        settlement.onFundsReserved(t0);
        assertThat(settlement.onRetriesExhausted(t0)).isEqualTo(PaymentSaga.TimeoutDecision.ESCALATE_TO_MANUAL_REVIEW);
        assertThat(settlement.step()).isEqualTo(SagaStep.MANUAL_REVIEW);
    }

    @Test
    void overdueDetectionAndRetryBudget() {
        PaymentSaga s = saga();
        assertThat(s.isOverdue(t0.plusSeconds(10), Duration.ofSeconds(30))).isFalse();
        assertThat(s.isOverdue(t0.plusSeconds(31), Duration.ofSeconds(30))).isTrue();
        s.recordRetry(t0.plusSeconds(31));
        assertThat(s.stepAttempts()).isEqualTo(1);
        assertThat(s.isOverdue(t0.plusSeconds(40), Duration.ofSeconds(30))).as("timer restarted").isFalse();
        s.recordRetry(t0);
        s.recordRetry(t0);
        assertThat(s.retriesExhausted(3)).isTrue();
    }
}
