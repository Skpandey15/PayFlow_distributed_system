package com.payflow.payment.adapter.out.settlement;

import com.payflow.payment.application.port.out.SettlementEvidencePort;
import com.payflow.payment.domain.PaymentId;
import com.payflow.settlement.application.port.in.SettlementOperationsUseCase;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Anti-Corruption Layer to the Settlement context's published operations port (in-process today). */
@Component
class SettlementContextAdapter implements SettlementEvidencePort {

    private final SettlementOperationsUseCase settlement;

    SettlementContextAdapter(SettlementOperationsUseCase settlement) {
        this.settlement = settlement;
    }

    @Override
    public Optional<Evidence> evidence(PaymentId paymentId) {
        return settlement.evidence(paymentId.value()).map(e -> new Evidence(e.railName(), e.localStatus(),
                e.submissionAttempts(), e.lastAttemptOutcome(), e.lastErrorCode(), e.lastAttemptAt(),
                RailState.valueOf(e.railRecord().status().name()),
                e.railRecord().declineReason() != null ? e.railRecord().declineReason() : e.railRecord().providerReference()));
    }

    @Override
    public RailState voidAtRail(PaymentId paymentId) {
        return RailState.valueOf(settlement.voidAtRail(paymentId.value()).status().name());
    }
}
