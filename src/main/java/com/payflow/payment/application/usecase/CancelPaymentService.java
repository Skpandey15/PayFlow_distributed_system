package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.CancelPaymentUseCase;
import com.payflow.payment.application.port.in.PaymentView;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.application.port.out.PaymentSagaRepositoryPort;
import com.payflow.payment.application.port.out.SagaCommandPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.domain.saga.PaymentSaga;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.NotFoundException;
import com.payflow.shared.application.TransactionRunner;

import java.time.Clock;
import java.time.Instant;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

/**
 * Cancellation in an asynchronous workflow. One transaction updates payment, saga and outbox:
 * <ul>
 *   <li>saga AWAITING_RISK: nothing is held, so the saga ends CANCELLED</li>
 *   <li>saga AWAITING_FUNDS: a reservation may exist or be in flight, so the saga starts compensating and
 *       issues ReleaseFunds. A release that overtakes its reserve leaves a tombstone, so the late reserve is refused.</li>
 *   <li>later: settlement may be in flight, so 409 (a refund, not a cancel, is the correct operation)</li>
 * </ul>
 * A concurrent saga reply that commits first bumps the saga version, and this request then fails with 409 instead of
 * overwriting it (lost update).
 */
public class CancelPaymentService implements CancelPaymentUseCase {

    private final PaymentRepositoryPort payments;
    private final PaymentSagaRepositoryPort sagas;
    private final SagaCommandPort commands;
    private final PaymentEventPublisherPort events;
    private final TransactionRunner tx;
    private final Clock clock;

    public CancelPaymentService(PaymentRepositoryPort payments, PaymentSagaRepositoryPort sagas,
                                SagaCommandPort commands, PaymentEventPublisherPort events, TransactionRunner tx,
                                Clock clock) {
        this.payments = payments;
        this.sagas = sagas;
        this.commands = commands;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public PaymentView cancel(Actor actor, PaymentId paymentId) {
        requirePermission(actor, PaymentPermissions.WRITE);
        return tx.inTransaction(() -> {
            Payment payment = PaymentAccess.loadVisible(payments, actor, paymentId);
            if (payment.status() == PaymentStatus.CANCELLED) {
                return PaymentView.from(payment);
            }
            PaymentSaga saga = sagas.findByPaymentId(paymentId)
                    .orElseThrow(() -> new NotFoundException("PAYMENT_SAGA_NOT_FOUND", "No saga for payment " + paymentId));
            Instant now = clock.instant();
            PaymentSaga.CancelDecision decision = saga.cancel(now);
            payment.cancel(now);
            sagas.update(saga);
            payments.update(payment);
            events.publish(payment.pullEvents());
            if (decision == PaymentSaga.CancelDecision.RELEASE_FUNDS) {
                commands.releaseFunds(payment, saga, "CANCELLED");
            }
            return PaymentView.from(payment);
        });
    }
}
