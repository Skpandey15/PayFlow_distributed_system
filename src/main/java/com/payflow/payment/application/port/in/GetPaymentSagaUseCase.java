package com.payflow.payment.application.port.in;

import com.payflow.payment.domain.PaymentId;
import com.payflow.shared.application.Actor;

import java.time.Instant;
import java.util.UUID;

/** Operator view of a payment's workflow, the first stop when debugging a "stuck" payment (payments:admin). */
public interface GetPaymentSagaUseCase {

    SagaView get(Actor actor, PaymentId paymentId);

    record SagaView(UUID sagaId, UUID paymentId, String step, String compensationReason, String outcomeReason,
                    int stepAttempts, Instant stepStartedAt, String correlationId) {
    }
}
