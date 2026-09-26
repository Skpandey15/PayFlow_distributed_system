package com.payflow.payment.application.port.in;

import com.payflow.payment.domain.PaymentId;
import com.payflow.shared.application.Actor;

public interface ProcessPaymentUseCase {

    /**
     * Submits an AUTHORIZED payment for settlement and records the outcome (SETTLED/FAILED).
     * Resumable and idempotent: calling it again on a PROCESSING payment resumes it, and on a SETTLED
     * payment it re-ensures the ledger posting.
     */
    PaymentView process(Actor actor, PaymentId paymentId);
}
