package com.payflow.account.application.port.out;

import com.payflow.account.domain.AccountBalance;
import com.payflow.shared.domain.AccountId;

import java.util.Optional;

public interface AccountBalanceRepositoryPort {

    void add(AccountBalance balance);

    /**
     * Loads the balance with a pessimistic write lock held until the transaction ends. Every funds mutation
     * must use this, which serialises concurrent reservations and captures on the same account.
     */
    Optional<AccountBalance> findForUpdate(AccountId accountId);

    Optional<AccountBalance> find(AccountId accountId);

    void update(AccountBalance balance);
}
