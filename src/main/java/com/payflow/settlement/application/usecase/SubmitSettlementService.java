package com.payflow.settlement.application.usecase;

import com.payflow.settlement.application.port.in.SubmitSettlementUseCase;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.GatewayInstruction;
import com.payflow.settlement.application.port.out.SettlementGatewayPort.GatewayResponse;
import com.payflow.settlement.application.port.out.SettlementRepositoryPort;
import com.payflow.settlement.application.port.out.SettlementRepositoryPort.DuplicateSettlementException;
import com.payflow.settlement.domain.Settlement;
import com.payflow.settlement.domain.SettlementRail;
import com.payflow.settlement.domain.SettlementStatus;
import com.payflow.shared.application.TransactionRunner;

import java.time.Clock;

/**
 * Submits a payment to its settlement rail using the "record intent, call out, record outcome" shape:
 *
 * <pre>
 *   TX1  find-or-create Settlement(PENDING)        -- unique(payment_id) makes this idempotent
 *   ---  gateway.submit(idempotencyKey=paymentId)  -- NO database transaction held open across the network call
 *   TX2  reload, complete/decline, optimistic-lock -- only if still PENDING (a concurrent resumer may have finished)
 * </pre>
 *
 * A crash between TX1 and TX2 leaves a PENDING settlement. Calling submit again resumes it with the
 * same provider idempotency key, so the rail deduplicates and money cannot move twice.
 */
public class SubmitSettlementService implements SubmitSettlementUseCase {

    private final SettlementRepositoryPort settlements;
    private final SettlementGatewayRouter router;
    private final TransactionRunner tx;
    private final Clock clock;

    public SubmitSettlementService(SettlementRepositoryPort settlements, SettlementGatewayRouter router,
                                   TransactionRunner tx, Clock clock) {
        this.settlements = settlements;
        this.router = router;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public SettlementResult submit(SubmitSettlementCommand command) {
        Settlement settlement = findOrCreate(command);
        if (settlement.status().isTerminal()) {
            return result(settlement);
        }

        GatewayResponse response = router.gatewayFor(settlement.rail()).submit(new GatewayInstruction(
                command.paymentId().toString(), settlement.amount(), settlement.paymentReference()));

        return tx.inTransaction(() -> {
            Settlement current = settlements.findByPaymentId(command.paymentId()).orElseThrow();
            if (!current.status().isTerminal()) {
                if (response.accepted()) {
                    current.complete(response.providerReference(), clock.instant());
                } else {
                    current.decline(response.declineReason(), clock.instant());
                }
                settlements.update(current);
            }
            return result(current);
        });
    }

    private Settlement findOrCreate(SubmitSettlementCommand command) {
        try {
            return tx.inTransaction(() -> settlements.findByPaymentId(command.paymentId()).orElseGet(() -> {
                Settlement created = Settlement.initiate(command.paymentId(), railFor(command.method()),
                        command.amount(), command.paymentReference(), clock.instant());
                settlements.add(created);
                return created;
            }));
        } catch (DuplicateSettlementException raced) {
            return tx.readOnly(() -> settlements.findByPaymentId(command.paymentId())).orElseThrow();
        }
    }

    private static SettlementRail railFor(Method method) {
        return switch (method) {
            case CARD -> SettlementRail.CARD_NETWORK;
            case UPI -> SettlementRail.UPI;
            case BANK_TRANSFER -> SettlementRail.BANK_TRANSFER;
        };
    }

    private static SettlementResult result(Settlement s) {
        return new SettlementResult(s.id(), s.paymentId(), s.status() == SettlementStatus.COMPLETED,
                s.providerReference(), s.declineReason());
    }
}
