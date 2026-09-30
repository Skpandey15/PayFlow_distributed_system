package com.payflow.account.application.port.out;

import com.payflow.account.application.port.out.DepositRepositoryPort.DepositRecord;
import com.payflow.account.domain.FundsReservation;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

import java.util.UUID;

/**
 * Announces funds outcomes. Implementations must write in the caller's transaction (Transactional Outbox),
 * so a balance change and its announcement commit atomically.
 */
public interface FundsEventPublisherPort {

    void fundsReserved(FundsReservation reservation);

    void reservationFailed(UUID paymentId, String reason);

    void fundsCaptured(FundsReservation reservation);

    void fundsReleased(UUID paymentId, AccountId payerAccountId, Money amount, String reason);

    void fundsDeposited(DepositRecord deposit);
}
