package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.RecoverStuckSagasUseCase;
import com.payflow.payment.application.port.out.CommandPublicationHealthPort;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.application.port.out.PaymentSagaRepositoryPort;
import com.payflow.payment.application.port.out.SagaCommandPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.saga.PaymentSaga;
import com.payflow.payment.domain.saga.SagaStep;
import com.payflow.shared.application.TransactionRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stuck-saga scanner (WP-01 finding M3). For every in-flight saga whose step exceeded its timeout:
 * <ol>
 *   <li>If retries remain, <b>re-issue the current step's command</b>. Every participant is idempotent per
 *       payment, so a re-issue either does the work that was lost (for example the command sat in a DLT) or
 *       re-announces the result that was lost.</li>
 *   <li>If retries are exhausted, compensate <b>only where it is safe</b> (see
 *       {@link PaymentSaga#onRetriesExhausted}); otherwise escalate to MANUAL_REVIEW.</li>
 * </ol>
 * Multi-replica safety: {@code FOR UPDATE SKIP LOCKED} gives each saga to one replica per run, and the saga's
 * optimistic version protects against a reply arriving concurrently.
 */
public class SagaRecoveryService implements RecoverStuckSagasUseCase {

    private final PaymentRepositoryPort payments;
    private final PaymentSagaRepositoryPort sagas;
    private final SagaCommandPort commands;
    private final PaymentEventPublisherPort events;
    private final TransactionRunner tx;
    private final Clock clock;
    private final SagaPolicy policy;
    private final CommandPublicationHealthPort publication;

    public SagaRecoveryService(PaymentRepositoryPort payments, PaymentSagaRepositoryPort sagas, SagaCommandPort commands,
                               PaymentEventPublisherPort events, TransactionRunner tx, Clock clock, SagaPolicy policy) {
        this(payments, sagas, commands, events, tx, clock, policy, () -> Duration.ZERO);
    }

    public SagaRecoveryService(PaymentRepositoryPort payments, PaymentSagaRepositoryPort sagas, SagaCommandPort commands,
                               PaymentEventPublisherPort events, TransactionRunner tx, Clock clock, SagaPolicy policy,
                               CommandPublicationHealthPort publication) {
        this.publication = publication;
        this.payments = payments;
        this.sagas = sagas;
        this.commands = commands;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
        this.policy = policy;
    }

    /** The decision for one saga, plus the commands to issue once saga and payment are persisted. */
    private record Recovery(RecoveryAction action, Runnable commands) {
    }

    /**
     * WP-03 backpressure rule: while the oldest unpublished command is older than {@code recoveryHoldAge}, recovery
     * holds. Measured in the WP-03 baseline: without it, an outbox backlog made steps look overdue, recovery re-issued
     * their commands into the same backlog (growing it), and after the retry budget rejected valid payments or sent
     * them to manual review: a self-reinforcing (metastable) failure caused by a slow relay, not by a participant.
     */
    @Override
    public RecoveryReport recoverOverdueSagas() {
        if (publication.oldestUnpublishedCommandAge().compareTo(policy.recoveryHoldAge()) > 0) {
            return RecoveryReport.held();
        }
        return tx.inTransaction(() -> {
            Instant now = clock.instant();
            List<RecoveredSaga> actions = new ArrayList<>();
            for (PaymentSaga saga : sagas.lockInFlightStartedBefore(now.minus(policy.shortestTimeout()),
                    policy.recoveryBatchSize())) {
                if (!saga.isOverdue(now, policy.timeoutFor(saga.step()))) {
                    continue;
                }
                Payment payment = payments.findById(saga.paymentId()).orElseThrow();
                SagaStep before = saga.step();
                int attemptsBefore = saga.stepAttempts();
                Recovery recovery = decide(saga, payment, now);
                // Persist state first, emit after (same ordering rule as the orchestrator).
                sagas.update(saga);
                var paymentEvents = payment.pullEvents();
                if (!paymentEvents.isEmpty()) {
                    payments.update(payment);
                    events.publish(paymentEvents);
                }
                recovery.commands().run();
                actions.add(new RecoveredSaga(saga.sagaId(), saga.paymentId(), saga.correlationId(), before,
                        attemptsBefore, recovery.action()));
            }
            return new RecoveryReport(actions);
        });
    }

    private Recovery decide(PaymentSaga saga, Payment payment, Instant now) {
        if (!saga.retriesExhausted(policy.maxStepAttempts())) {
            saga.recordRetry(now);
            return new Recovery(RecoveryAction.COMMAND_REISSUED, () -> reissueCurrentCommand(saga, payment));
        }
        return switch (saga.onRetriesExhausted(now)) {
            case REJECT_PAYMENT -> {
                payment.reject(saga.outcomeReason(), now);
                yield new Recovery(RecoveryAction.PAYMENT_REJECTED, () -> { });
            }
            case REJECT_AND_RELEASE_FUNDS -> {
                payment.reject(saga.outcomeReason(), now);
                yield new Recovery(RecoveryAction.COMPENSATION_STARTED,
                        () -> commands.releaseFunds(payment, saga, "TIMEOUT"));
            }
            case ESCALATE_TO_MANUAL_REVIEW -> new Recovery(RecoveryAction.ESCALATED_TO_MANUAL_REVIEW, () -> { });
        };
    }

    private void reissueCurrentCommand(PaymentSaga saga, Payment payment) {
        switch (saga.step()) {
            case AWAITING_RISK -> commands.requestRiskAssessment(payment, saga);
            case AWAITING_FUNDS -> commands.reserveFunds(payment, saga);
            case AWAITING_SETTLEMENT -> commands.submitSettlement(payment, saga);
            case AWAITING_CAPTURE -> commands.captureFunds(payment, saga);
            case COMPENSATING -> commands.releaseFunds(payment, saga, saga.compensationReason().name());
            default -> throw new IllegalStateException("Saga " + saga.sagaId() + " is not in flight");
        }
    }
}
