package com.payflow.payment.domain.saga;

import com.payflow.payment.domain.PaymentId;

import java.time.Instant;
import java.util.UUID;

public record PaymentSagaSnapshot(
        UUID sagaId,
        PaymentId paymentId,
        SagaStep step,
        CompensationReason compensationReason,
        String outcomeReason,
        int stepAttempts,
        Instant stepStartedAt,
        String correlationId,
        CheckoutContext checkout,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
