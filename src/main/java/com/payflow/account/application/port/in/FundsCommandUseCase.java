package com.payflow.account.application.port.in;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

import java.util.UUID;

/**
 * Saga participant API of the Account context: reserve, capture and release funds for a payment.
 * Every operation is idempotent per paymentId and always (re-)announces its outcome as an event, so a
 * re-sent command, whether from recovery or replay, gets the same answer instead of silence.
 */
public interface FundsCommandUseCase {

    void reserve(ReserveFunds command);

    void capture(UUID paymentId);

    void release(ReleaseFunds command);

    record ReserveFunds(UUID paymentId, AccountId payerAccountId, AccountId payeeAccountId, Money amount) {
    }

    record ReleaseFunds(UUID paymentId, AccountId payerAccountId, Money amount, String reason) {
    }
}
