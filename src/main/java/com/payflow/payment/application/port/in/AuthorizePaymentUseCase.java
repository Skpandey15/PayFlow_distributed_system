package com.payflow.payment.application.port.in;

import com.payflow.payment.domain.PaymentId;
import com.payflow.shared.application.Actor;

public interface AuthorizePaymentUseCase {

    /**
     * Runs eligibility and risk checks and moves CREATED to AUTHORIZED or REJECTED.
     * Idempotent: once decided, the decision is returned unchanged.
     */
    PaymentView authorize(AuthorizePaymentCommand command);

    record AuthorizePaymentCommand(Actor actor, PaymentId paymentId, CheckoutChannel channel) {
    }

    /** Optional device/network context captured at checkout, used only as fraud evidence. */
    record CheckoutChannel(String deviceId, String ipAddress, String userAgent, String countryCode) {
    }
}
