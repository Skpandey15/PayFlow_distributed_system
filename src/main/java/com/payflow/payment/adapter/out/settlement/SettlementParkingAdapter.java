package com.payflow.payment.adapter.out.settlement;

import com.payflow.payment.application.port.out.SettlementParkingPort;
import com.payflow.payment.domain.PaymentId;
import com.payflow.settlement.application.port.in.ResumeParkedSettlementsUseCase;
import org.springframework.stereotype.Component;

/** Anti-Corruption Layer to the Settlement context's parking state (in-process today). */
@Component
class SettlementParkingAdapter implements SettlementParkingPort {

    private final ResumeParkedSettlementsUseCase settlement;

    SettlementParkingAdapter(ResumeParkedSettlementsUseCase settlement) {
        this.settlement = settlement;
    }

    @Override
    public boolean isParked(PaymentId paymentId) {
        return settlement.isParked(paymentId.value());
    }
}
