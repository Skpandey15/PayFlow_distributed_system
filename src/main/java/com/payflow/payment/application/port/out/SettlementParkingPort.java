package com.payflow.payment.application.port.out;

import com.payflow.payment.domain.PaymentId;

/**
 * Whether the payment's settlement is parked: held by the Settlement context while the rail's circuit is open, and
 * re-submitted by its resumer (review R-2). A local lookup only, cheap enough for every recovery scan.
 */
@FunctionalInterface
public interface SettlementParkingPort {

    boolean isParked(PaymentId paymentId);
}
