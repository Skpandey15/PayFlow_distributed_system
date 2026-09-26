package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.NotFoundException;

/** Loading and object-level authorisation rules shared by the payment use cases. */
final class PaymentAccess {

    private PaymentAccess() {
    }

    static Payment load(PaymentRepositoryPort payments, PaymentId id) {
        return payments.findById(id).orElseThrow(() -> notFound(id));
    }

    /**
     * Object-level authorisation (OWASP API1: BOLA). A payment is visible to its initiator or to an
     * admin. Otherwise we answer "not found" rather than "forbidden" so ids cannot be probed.
     */
    static Payment loadVisible(PaymentRepositoryPort payments, Actor actor, PaymentId id) {
        Payment payment = load(payments, id);
        if (!payment.isInitiatedBy(actor.subject()) && !actor.hasPermission(PaymentPermissions.ADMIN)) {
            throw notFound(id);
        }
        return payment;
    }

    private static NotFoundException notFound(PaymentId id) {
        return new NotFoundException("PAYMENT_NOT_FOUND", "Payment " + id + " not found");
    }
}
