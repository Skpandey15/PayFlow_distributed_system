package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.CancelPaymentUseCase;
import com.payflow.payment.application.port.in.PaymentView;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.TransactionRunner;

import java.time.Clock;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

/**
 * Transaction boundary: load, transition, save and publish in one short transaction. A concurrent
 * authorize or process that commits first bumps the version, and this transaction then fails with
 * ConcurrencyConflictException (409) instead of silently overwriting a later state (lost update).
 */
public class CancelPaymentService implements CancelPaymentUseCase {

    private final PaymentRepositoryPort payments;
    private final PaymentEventPublisherPort events;
    private final TransactionRunner tx;
    private final Clock clock;

    public CancelPaymentService(PaymentRepositoryPort payments, PaymentEventPublisherPort events,
                                TransactionRunner tx, Clock clock) {
        this.payments = payments;
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
            payment.cancel(clock.instant());
            payments.update(payment);
            events.publish(payment.pullEvents());
            return PaymentView.from(payment);
        });
    }
}
