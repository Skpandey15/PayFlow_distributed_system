package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.GetPaymentSagaUseCase;
import com.payflow.payment.application.port.in.PaymentSagaUseCase;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.application.port.out.PaymentSagaRepositoryPort;
import com.payflow.payment.application.port.out.SagaCommandPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.saga.PaymentSaga;
import com.payflow.payment.domain.saga.SagaStep;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.NotFoundException;
import com.payflow.shared.application.TransactionRunner;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

/**
 * The saga <b>orchestrator</b>. It reacts to participant replies, advances the saga, updates the payment and
 * issues the next command, all in <b>one local transaction</b> (saga row + payment row + outbox rows). Either
 * everything commits, or nothing does and the reply is redelivered.
 *
 * <pre>
 *  RiskAssessed(approve)        → ReserveFunds
 *  RiskAssessed(decline)        → payment REJECTED
 *  FundsReserved                → payment AUTHORIZED → PROCESSING, SubmitSettlement
 *  FundsReservationFailed       → payment REJECTED
 *  SettlementCompleted          → CaptureFunds
 *  SettlementDeclined           → payment FAILED, ReleaseFunds (compensation)
 *  FundsCaptured                → payment SETTLED
 *  FundsReleased                → compensation complete
 * </pre>
 * A reply that does not match the saga's current step (duplicate with a new eventId, late after timeout,
 * overtaken by cancellation) is reported as not applied and changes nothing.
 */
public class PaymentSagaService implements PaymentSagaUseCase, GetPaymentSagaUseCase {

    private final PaymentRepositoryPort payments;
    private final PaymentSagaRepositoryPort sagas;
    private final SagaCommandPort commands;
    private final PaymentEventPublisherPort events;
    private final TransactionRunner tx;
    private final Clock clock;

    public PaymentSagaService(PaymentRepositoryPort payments, PaymentSagaRepositoryPort sagas, SagaCommandPort commands,
                              PaymentEventPublisherPort events, TransactionRunner tx, Clock clock) {
        this.payments = payments;
        this.sagas = sagas;
        this.commands = commands;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    /** Commands to issue after the new state is persisted; empty = the reply was stale and changed nothing. */
    private static Optional<Runnable> issue(Runnable commands) {
        return Optional.of(commands);
    }

    private static final Optional<Runnable> NO_COMMAND = Optional.of(() -> { });
    private static final Optional<Runnable> STALE = Optional.empty();

    @Override
    public SagaTransition onRiskAssessed(PaymentId paymentId, boolean approved, String reason) {
        return step(paymentId, (saga, payment) -> {
            Instant now = clock.instant();
            if (approved) {
                return saga.onRiskApproved(now) ? issue(() -> commands.reserveFunds(payment, saga)) : STALE;
            }
            if (!saga.onRiskDeclined(reason, now)) {
                return STALE;
            }
            payment.reject(reason == null ? "RISK_DECLINED" : reason, now);
            return NO_COMMAND;
        });
    }

    @Override
    public SagaTransition onFundsReserved(PaymentId paymentId) {
        return step(paymentId, (saga, payment) -> {
            Instant now = clock.instant();
            if (!saga.onFundsReserved(now)) {
                return STALE;
            }
            payment.authorize(now);
            payment.startProcessing(now);
            return issue(() -> commands.submitSettlement(payment, saga));
        });
    }

    @Override
    public SagaTransition onFundsReservationFailed(PaymentId paymentId, String reason) {
        return step(paymentId, (saga, payment) -> {
            Instant now = clock.instant();
            if (!saga.onFundsRejected(reason, now)) {
                return STALE;
            }
            payment.reject(reason, now);
            return NO_COMMAND;
        });
    }

    @Override
    public SagaTransition onSettlementCompleted(PaymentId paymentId) {
        return step(paymentId, (saga, payment) -> saga.onSettlementCompleted(clock.instant())
                ? issue(() -> commands.captureFunds(payment, saga)) : STALE);
    }

    @Override
    public SagaTransition onSettlementDeclined(PaymentId paymentId, String reason) {
        return step(paymentId, (saga, payment) -> {
            Instant now = clock.instant();
            if (!saga.onSettlementDeclined(reason, now)) {
                return STALE;
            }
            payment.markFailed("SETTLEMENT_DECLINED:" + reason, now);
            return issue(() -> commands.releaseFunds(payment, saga, "SETTLEMENT_DECLINED"));
        });
    }

    @Override
    public SagaTransition onFundsCaptured(PaymentId paymentId) {
        return step(paymentId, (saga, payment) -> {
            Instant now = clock.instant();
            if (!saga.onFundsCaptured(now)) {
                return STALE;
            }
            payment.markSettled(now);
            return NO_COMMAND;
        });
    }

    @Override
    public SagaTransition onFundsReleased(PaymentId paymentId) {
        return step(paymentId, (saga, payment) -> saga.onFundsReleased(clock.instant()) ? NO_COMMAND : STALE);
    }

    @Override
    public SagaView get(Actor actor, PaymentId paymentId) {
        requirePermission(actor, PaymentPermissions.ADMIN);
        PaymentSaga s = tx.readOnly(() -> sagas.findByPaymentId(paymentId)).orElseThrow(() -> notFound(paymentId));
        return new SagaView(s.sagaId(), s.paymentId().value(), s.step().name(),
                s.compensationReason() == null ? null : s.compensationReason().name(), s.outcomeReason(),
                s.stepAttempts(), s.stepStartedAt(), s.correlationId());
    }

    /**
     * Loads saga and payment, applies the transition, then persists in a fixed order: versioned UPDATEs of saga
     * and payment first (row locks, fail fast on a concurrent change), outbox inserts after. Per-payment outbox
     * order therefore always equals commit order.
     */
    private SagaTransition step(PaymentId paymentId, BiFunction<PaymentSaga, Payment, Optional<Runnable>> transition) {
        return tx.inTransaction(() -> {
            PaymentSaga saga = sagas.findByPaymentId(paymentId).orElseThrow(() -> notFound(paymentId));
            Payment payment = payments.findById(paymentId).orElseThrow(() -> notFound(paymentId));
            SagaStep before = saga.step();
            Instant stepStartedAt = saga.stepStartedAt();
            Optional<Runnable> issueCommands = transition.apply(saga, payment);
            if (issueCommands.isEmpty()) {
                return new SagaTransition(saga.sagaId(), paymentId, before, saga.step(), false, saga.createdAt(),
                        stepStartedAt);
            }
            sagas.update(saga);
            var paymentEvents = payment.pullEvents();
            if (!paymentEvents.isEmpty()) {
                payments.update(payment);
                events.publish(paymentEvents);
            }
            issueCommands.get().run();
            return new SagaTransition(saga.sagaId(), paymentId, before, saga.step(), true, saga.createdAt(),
                    stepStartedAt);
        });
    }

    private static NotFoundException notFound(PaymentId id) {
        return new NotFoundException("PAYMENT_SAGA_NOT_FOUND", "No saga for payment " + id);
    }
}
