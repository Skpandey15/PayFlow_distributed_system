package com.payflow.payment.application.port.out;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.saga.PaymentSaga;

/**
 * Commands the saga orchestrator sends to participants. Implementations must write them in the caller's
 * transaction (Transactional Outbox): a saga step and the command it issues commit atomically, so a crash can
 * never leave a saga waiting for a reply to a command that was never sent.
 */
public interface SagaCommandPort {

    void requestRiskAssessment(Payment payment, PaymentSaga saga);

    void reserveFunds(Payment payment, PaymentSaga saga);

    void submitSettlement(Payment payment, PaymentSaga saga);

    void captureFunds(Payment payment, PaymentSaga saga);

    void releaseFunds(Payment payment, PaymentSaga saga, String reason);
}
