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
        Instant updatedAt,
        SagaStep escalatedFrom) {

    /** Snapshot of a saga that was never escalated to manual review. */
    public PaymentSagaSnapshot(UUID sagaId, PaymentId paymentId, SagaStep step, CompensationReason compensationReason,
                               String outcomeReason, int stepAttempts, Instant stepStartedAt, String correlationId,
                               CheckoutContext checkout, long version, Instant createdAt, Instant updatedAt) {
        this(sagaId, paymentId, step, compensationReason, outcomeReason, stepAttempts, stepStartedAt, correlationId,
                checkout, version, createdAt, updatedAt, null);
    }
}
