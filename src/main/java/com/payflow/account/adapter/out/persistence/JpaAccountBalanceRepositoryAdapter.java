package com.payflow.account.adapter.out.persistence;

import com.payflow.account.application.port.out.AccountBalanceRepositoryPort;
import com.payflow.account.domain.AccountBalance;
import com.payflow.shared.application.ConcurrencyConflictException;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.Optional;

@Component
class JpaAccountBalanceRepositoryAdapter implements AccountBalanceRepositoryPort {

    private final SpringDataAccountBalanceRepository repository;

    JpaAccountBalanceRepositoryAdapter(SpringDataAccountBalanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(AccountBalance b) {
        repository.saveAndFlush(new AccountBalanceJpaEntity(b.accountId().value(), b.currency().getCurrencyCode(),
                b.available().amount(), b.reserved().amount(), b.updatedAt()));
    }

    @Override
    public Optional<AccountBalance> findForUpdate(AccountId accountId) {
        return repository.findForUpdate(accountId.value()).map(JpaAccountBalanceRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<AccountBalance> find(AccountId accountId) {
        return repository.findById(accountId.value()).map(JpaAccountBalanceRepositoryAdapter::toDomain);
    }

    @Override
    public void update(AccountBalance b) {
        AccountBalanceJpaEntity entity = repository.findById(b.accountId().value()).orElseThrow();
        if (entity.getVersion() != b.version()) {
            throw new ConcurrencyConflictException("Balance of " + b.accountId() + " changed concurrently", null);
        }
        entity.apply(b.available().amount(), b.reserved().amount(), b.updatedAt());
        try {
            repository.saveAndFlush(entity);
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrencyConflictException("Balance of " + b.accountId() + " changed concurrently", e);
        }
    }

    private static AccountBalance toDomain(AccountBalanceJpaEntity e) {
        Currency currency = Money.currency(e.getCurrency());
        return AccountBalance.rehydrate(new AccountId(e.getAccountId()), currency, Money.of(e.getAvailable(), currency),
                Money.of(e.getReserved(), currency), e.getVersion(), e.getUpdatedAt());
    }
}
