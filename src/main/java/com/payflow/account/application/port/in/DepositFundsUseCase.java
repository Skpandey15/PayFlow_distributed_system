package com.payflow.account.application.port.in;

import com.payflow.shared.application.Actor;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

import java.util.UUID;

/** Treasury top-up of an account (scope {@code funds:deposit}). Idempotent per client-supplied depositId. */
public interface DepositFundsUseCase {

    DepositResult deposit(DepositCommand command);

    record DepositCommand(Actor actor, AccountId accountId, UUID depositId, Money amount) {
    }

    record DepositResult(UUID depositId, AccountView account, boolean replayed) {
    }
}
