package com.payflow.payment.application.port.in;

import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.saga.SagaStep;

import java.util.UUID;

/**
 * Participant replies that drive the payment saga (called by the Kafka inbound adapter). Each call is one
 * local transaction: saga step + payment state + outgoing command/event outbox rows.
 */
public interface PaymentSagaUseCase {

    SagaTransition onRiskAssessed(PaymentId paymentId, boolean approved, String reason);

    SagaTransition onFundsReserved(PaymentId paymentId);

    SagaTransition onFundsReservationFailed(PaymentId paymentId, String reason);

    SagaTransition onSettlementCompleted(PaymentId paymentId);

    SagaTransition onSettlementDeclined(PaymentId paymentId, String reason);

    SagaTransition onFundsCaptured(PaymentId paymentId);

    SagaTransition onFundsReleased(PaymentId paymentId);

    /** Result for logging/metrics at the adapter boundary. {@code applied=false} means stale/duplicate reply. */
    record SagaTransition(UUID sagaId, PaymentId paymentId, SagaStep from, SagaStep to, boolean applied) {
    }
}
