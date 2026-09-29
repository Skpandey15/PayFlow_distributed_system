package com.payflow.settlement.application.usecase;

import com.payflow.settlement.application.port.in.SettlementOperationsUseCase;
import com.payflow.settlement.application.port.out.SettlementGatewayPort;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.GatewayResponse;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.GatewayUnavailableException;
import com.payflow.settlement.application.port.out.SettlementRepositoryPort;
import com.payflow.settlement.domain.Settlement;
import com.payflow.shared.application.NotFoundException;
import com.payflow.shared.application.TransactionRunner;

import java.util.Optional;
import java.util.UUID;

public class SettlementOperationsService implements SettlementOperationsUseCase {

    private final SettlementRepositoryPort settlements;
    private final SettlementGatewayRouter router;
    private final TransactionRunner tx;

    public SettlementOperationsService(SettlementRepositoryPort settlements, SettlementGatewayRouter router,
                                       TransactionRunner tx) {
        this.settlements = settlements;
        this.router = router;
        this.tx = tx;
    }

    @Override
    public Optional<SettlementEvidence> evidence(UUID paymentId) {
        return tx.readOnly(() -> settlements.findByPaymentId(paymentId)).map(s -> {
            RailRecord rail;
            try {
                rail = router.gatewayFor(s.rail()).inquire(paymentId.toString())
                        .map(SettlementOperationsService::record)
                        .orElse(new RailRecord(RailStatus.NOT_FOUND, null, null));
            } catch (GatewayUnavailableException e) {
                // Evidence is still useful without the rail's answer; decisions that need it are refused later.
                rail = new RailRecord(RailStatus.UNREACHABLE, null, e.code());
            }
            return new SettlementEvidence(s.id(), s.rail().name(), s.status().name(), s.submissionAttempts(),
                    s.lastAttemptOutcome(), s.lastErrorCode(), s.lastAttemptAt(), rail);
        });
    }

    @Override
    public RailRecord voidAtRail(UUID paymentId) {
        Settlement s = tx.readOnly(() -> settlements.findByPaymentId(paymentId))
                .orElseThrow(() -> new NotFoundException("SETTLEMENT_NOT_FOUND", "No settlement for payment " + paymentId));
        SettlementGatewayPort gateway = router.gatewayFor(s.rail());
        return record(gateway.voidInstruction(paymentId.toString()));
    }

    private static RailRecord record(GatewayResponse r) {
        return new RailRecord(r.accepted() ? RailStatus.ACCEPTED : RailStatus.DECLINED, r.providerReference(),
                r.declineReason());
    }
}
