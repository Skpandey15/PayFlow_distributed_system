package com.payflow.account.application.port.out;

import com.payflow.account.domain.Account;
import com.payflow.shared.domain.AccountId;

import java.util.Optional;

public interface AccountRepositoryPort {

    void add(Account account);

    /** Persists changes; fails with ConcurrencyConflictException if the stored version moved on. */
    void update(Account account);

    Optional<Account> findById(AccountId id);
}
