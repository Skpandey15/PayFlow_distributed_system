package com.payflow.payment.application.port.in;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.domain.Money;

import java.time.Instant;
import java.util.UUID;

/** Immutable read model returned by payment use cases, so the mutable aggregate never leaks to adapters. */
public record PaymentView(
        UUID id,
        UUID payerAccountId,
        UUID payeeAccountId,
        Money amount,
        PaymentMethod method,
        PaymentStatus status,
        String reference,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentView from(Payment p) {
        return new PaymentView(p.id().value(), p.payerAccountId().value(), p.payeeAccountId().value(), p.amount(),
                p.method(), p.status(), p.reference(), p.failureReason(), p.createdAt(), p.updatedAt());
    }
}
