package com.payflow.account.adapter.out.persistence;

import com.payflow.account.application.port.out.AccountRepositoryPort;
import com.payflow.account.domain.Account;
import com.payflow.account.domain.AccountSnapshot;
import com.payflow.shared.application.ConcurrencyConflictException;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class JpaAccountRepositoryAdapter implements AccountRepositoryPort {

    private final SpringDataAccountRepository repository;

    JpaAccountRepositoryAdapter(SpringDataAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(Account account) {
        AccountSnapshot s = account.snapshot();
        repository.saveAndFlush(new AccountJpaEntity(s.id().value(), s.ownerSubject(), s.displayName(),
                s.currency().getCurrencyCode(), s.status(), s.createdAt(), s.updatedAt()));
    }

    @Override
    public void update(Account account) {
        AccountJpaEntity entity = repository.findById(account.id().value())
                .orElseThrow(() -> new IllegalStateException("Cannot update unknown account " + account.id()));
        if (entity.getVersion() != account.version()) {
            throw new ConcurrencyConflictException("Account " + account.id() + " was modified concurrently", null);
        }
        entity.applyState(account.displayName(), account.status(), account.updatedAt());
        try {
            repository.saveAndFlush(entity);
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrencyConflictException("Account " + account.id() + " was modified concurrently", e);
        }
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return repository.findById(id.value()).map(e -> Account.rehydrate(new AccountSnapshot(
                new AccountId(e.getId()), e.getOwnerSubject(), e.getDisplayName(), Money.currency(e.getCurrency()),
                e.getStatus(), e.getCreatedAt(), e.getUpdatedAt(), e.getVersion())));
    }
}
