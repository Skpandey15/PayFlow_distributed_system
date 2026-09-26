package com.payflow.account.application.usecase;

import com.payflow.account.application.port.in.AccountView;
import com.payflow.account.application.port.in.FreezeAccountUseCase;
import com.payflow.account.application.port.in.GetAccountUseCase;
import com.payflow.account.application.port.in.LookupAccountUseCase;
import com.payflow.account.application.port.in.OpenAccountUseCase;
import com.payflow.account.application.port.out.AccountBalanceRepositoryPort;
import com.payflow.account.application.port.out.AccountRepositoryPort;
import com.payflow.account.domain.Account;
import com.payflow.account.domain.AccountBalance;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.NotFoundException;
import com.payflow.shared.application.TransactionRunner;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

import java.time.Clock;
import java.util.Optional;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

/**
 * Application service for the (thin) Account context. It implements several narrow inbound ports.
 * Callers depend only on the port they need (Interface Segregation), while the small amount of logic
 * stays in one cohesive class instead of four one-method services.
 */
public class AccountService implements OpenAccountUseCase, GetAccountUseCase, FreezeAccountUseCase, LookupAccountUseCase {

    private final AccountRepositoryPort accounts;
    private final AccountBalanceRepositoryPort balances;
    private final TransactionRunner tx;
    private final Clock clock;

    public AccountService(AccountRepositoryPort accounts, AccountBalanceRepositoryPort balances, TransactionRunner tx,
                          Clock clock) {
        this.accounts = accounts;
        this.balances = balances;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public AccountView open(OpenAccountCommand command) {
        requirePermission(command.actor(), AccountPermissions.WRITE);
        Account account = Account.open(AccountId.newId(), command.actor().subject(), command.displayName(),
                Money.currency(command.currencyCode()), clock.instant());
        AccountBalance balance = AccountBalance.open(account.id(), account.currency(), account.createdAt());
        tx.inTransaction(() -> {
            accounts.add(account);
            balances.add(balance);
            return null;
        });
        return AccountView.from(account, balance);
    }

    @Override
    public AccountView get(Actor actor, AccountId accountId) {
        requirePermission(actor, AccountPermissions.READ);
        return tx.readOnly(() -> {
            Account account = accounts.findById(accountId)
                    .filter(a -> a.isOwnedBy(actor.subject()) || actor.hasPermission(AccountPermissions.ADMIN))
                    .orElseThrow(() -> notFound(accountId));
            return AccountView.from(account, balances.find(accountId).orElseThrow());
        });
    }

    @Override
    public AccountView freeze(Actor actor, AccountId accountId) {
        requirePermission(actor, AccountPermissions.ADMIN);
        return tx.inTransaction(() -> {
            Account account = accounts.findById(accountId).orElseThrow(() -> notFound(accountId));
            account.freeze(clock.instant());
            accounts.update(account);
            return AccountView.from(account, balances.find(accountId).orElseThrow());
        });
    }

    @Override
    public Optional<AccountSummary> findAccount(AccountId accountId) {
        return tx.readOnly(() -> accounts.findById(accountId))
                .map(a -> new AccountSummary(a.id(), a.ownerSubject(), a.currency().getCurrencyCode(), a.canTransact()));
    }

    private static NotFoundException notFound(AccountId id) {
        return new NotFoundException("ACCOUNT_NOT_FOUND", "Account " + id + " not found");
    }
}
