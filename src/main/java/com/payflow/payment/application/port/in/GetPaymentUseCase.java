package com.payflow.payment.application.port.in;

import com.payflow.payment.domain.PaymentId;
import com.payflow.shared.application.Actor;

public interface GetPaymentUseCase {

    /** Returns the payment if visible to the actor (initiator or payments:admin); otherwise NotFound. */
    PaymentView get(Actor actor, PaymentId paymentId);
}
