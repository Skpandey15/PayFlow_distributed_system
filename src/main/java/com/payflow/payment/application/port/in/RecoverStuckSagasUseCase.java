package com.payflow.payment.application.port.in;

import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.saga.SagaStep;

import java.util.List;
import java.util.UUID;

/** Stuck-workflow recovery (fixes WP-01 finding M3). Safe to run on every replica concurrently. */
public interface RecoverStuckSagasUseCase {

    RecoveryReport recoverOverdueSagas();

    /** SETTLEMENT_PARKED: deferred without a command or a retry, because the Settlement context is resuming it. */
    enum RecoveryAction { COMMAND_REISSUED, SETTLEMENT_PARKED, PAYMENT_REJECTED, COMPENSATION_STARTED,
        ESCALATED_TO_MANUAL_REVIEW }

    record RecoveredSaga(UUID sagaId, PaymentId paymentId, String correlationId, SagaStep stepBefore,
                         int attemptsBefore, RecoveryAction action) {
    }

    /**
     * @param heldBecauseCommandsUnpublished recovery did not run: outgoing commands are not reaching the broker, so
     *                                       "overdue" steps are waiting for publication, not for a participant
     */
    record RecoveryReport(List<RecoveredSaga> actions, boolean heldBecauseCommandsUnpublished) {

        public RecoveryReport(List<RecoveredSaga> actions) {
            this(actions, false);
        }

        public static RecoveryReport held() {
            return new RecoveryReport(List.of(), true);
        }

        public long count(RecoveryAction action) {
            return actions.stream().filter(a -> a.action() == action).count();
        }
    }
}
