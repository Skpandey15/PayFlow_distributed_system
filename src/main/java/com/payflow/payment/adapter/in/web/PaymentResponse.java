package com.payflow.payment.adapter.in.web;

import com.payflow.payment.application.port.in.PaymentView;

import java.time.Instant;
import java.util.UUID;

/**
 * Public API representation (versioned with the URI, /api/v1). It is independent of both the JPA entity
 * and the domain aggregate, so storage or domain refactoring does not break clients.
 */
public record PaymentResponse(
        UUID id,
        UUID payerAccountId,
        UUID payeeAccountId,
        String amount,
        String currency,
        String method,
        String status,
        String reference,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    static PaymentResponse from(PaymentView v) {
        return new PaymentResponse(v.id(), v.payerAccountId(), v.payeeAccountId(), v.amount().amount().toPlainString(),
                v.amount().currencyCode(), v.method().name(), v.status().name(), v.reference(), v.failureReason(),
                v.createdAt(), v.updatedAt());
    }
}
