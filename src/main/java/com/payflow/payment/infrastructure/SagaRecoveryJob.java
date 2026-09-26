package com.payflow.payment.infrastructure;

import com.payflow.payment.application.port.in.RecoverStuckSagasUseCase;
import com.payflow.payment.application.port.in.RecoverStuckSagasUseCase.RecoveredSaga;
import com.payflow.payment.application.port.in.RecoverStuckSagasUseCase.RecoveryAction;
import com.payflow.platform.messaging.MessageContext;
import com.payflow.platform.messaging.MessagingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the stuck-saga scanner on every replica (SKIP LOCKED makes that safe) and owns the logging of recovery
 * decisions: WARN for a re-issued command or compensation, ERROR for manual-review escalation (the alerting
 * signal, together with {@code payflow.saga.recovery{action=ESCALATED_TO_MANUAL_REVIEW}}).
 */
@Component
class SagaRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger("payflow.saga.recovery");

    private final RecoverStuckSagasUseCase recovery;
    private final MessagingMetrics metrics;

    SagaRecoveryJob(RecoverStuckSagasUseCase recovery, MessagingMetrics metrics) {
        this.recovery = recovery;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${payflow.saga.recovery-interval-ms:10000}",
            initialDelayString = "${payflow.saga.recovery-interval-ms:10000}")
    void recover() {
        try {
            for (RecoveredSaga r : recovery.recoverOverdueSagas().actions()) {
                report(r);
            }
        } catch (RuntimeException e) {
            // Database unreachable, or a concurrent reply won the optimistic lock: the next run retries.
            log.atWarn().addKeyValue("errorCode", e.getClass().getSimpleName()).log("saga recovery run failed");
        }
    }

    private void report(RecoveredSaga r) {
        metrics.count("payflow.saga.recovery", "action", r.action().name(), "step", r.stepBefore().name());
        try {
            MessageContext.put(MessageContext.SAGA_ID, r.sagaId());
            MessageContext.put(MessageContext.CORRELATION_ID, r.correlationId());
            MessageContext.put(MessageContext.AGGREGATE_ID, r.paymentId());
            var event = (r.action() == RecoveryAction.ESCALATED_TO_MANUAL_REVIEW ? log.atError() : log.atWarn())
                    .addKeyValue("sagaStep", r.stepBefore())
                    .addKeyValue("stepAttempts", r.attemptsBefore())
                    .addKeyValue("recoveryAction", r.action());
            event.log(r.action() == RecoveryAction.ESCALATED_TO_MANUAL_REVIEW
                    ? "saga stuck with unknown outcome; escalated to manual review"
                    : "overdue saga recovered");
        } finally {
            MessageContext.clearWorkContext();
        }
    }
}
