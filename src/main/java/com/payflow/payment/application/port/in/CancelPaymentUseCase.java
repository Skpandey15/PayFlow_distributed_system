package com.payflow.payment.application.port.in;

import com.payflow.payment.domain.PaymentId;
import com.payflow.shared.application.Actor;

public interface CancelPaymentUseCase {

    /** Idempotent: cancelling an already-cancelled payment returns it unchanged. */
    PaymentView cancel(Actor actor, PaymentId paymentId);
}
