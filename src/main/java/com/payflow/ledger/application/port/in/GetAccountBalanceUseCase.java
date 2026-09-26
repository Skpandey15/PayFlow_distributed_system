package com.payflow.ledger.application.port.in;

import com.payflow.shared.application.Actor;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

public interface GetAccountBalanceUseCase {

    /** Balance derived from all posted ledger lines of the account in the given currency. */
    BalanceView balance(Actor actor, AccountId accountId, String currencyCode);

    record BalanceView(AccountId accountId, Money balance) {
    }
}
