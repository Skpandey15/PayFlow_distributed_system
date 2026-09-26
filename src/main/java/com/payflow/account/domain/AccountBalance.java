package com.payflow.account.domain;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.DomainRuleViolationException;
import com.payflow.shared.domain.Money;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/**
 * Spendable funds of one account: {@code available} (free to spend) and {@code reserved} (held for in-flight
 * payments). Invariants: both are non-negative and in the account currency. A reservation can never exceed
 * {@code available}; this is what prevents overdraft (WP-01 finding M2).
 *
 * <p>Concurrency is not handled in memory. The repository loads the balance with a pessimistic row lock
 * ({@code SELECT … FOR UPDATE}) for every mutation, so two concurrent reservations against one account are
 * serialised by PostgreSQL. The second one sees the first one's result. CHECK constraints back this up.
 */
public final class AccountBalance {

    private final AccountId accountId;
    private final Currency currency;
    private final long version;
    private Money available;
    private Money reserved;
    private Instant updatedAt;

    private AccountBalance(AccountId accountId, Currency currency, Money available, Money reserved, long version,
                           Instant updatedAt) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.available = requireCurrency(available, currency);
        this.reserved = requireCurrency(reserved, currency);
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public static AccountBalance open(AccountId accountId, Currency currency, Instant now) {
        return new AccountBalance(accountId, currency, Money.zero(currency), Money.zero(currency), 0L, now);
    }

    public static AccountBalance rehydrate(AccountId accountId, Currency currency, Money available, Money reserved,
                                           long version, Instant updatedAt) {
        return new AccountBalance(accountId, currency, available, reserved, version, updatedAt);
    }

    public boolean canReserve(Money amount) {
        requireCurrency(amount, currency);
        return amount.isPositive() && !amount.isGreaterThan(available);
    }

    /** Hold funds for a payment: available → reserved. */
    public void reserve(Money amount, Instant now) {
        requirePositive(amount);
        if (!canReserve(amount)) {
            throw new DomainRuleViolationException("FUNDS_INSUFFICIENT",
                    "Insufficient available funds on account " + accountId);
        }
        available = available.minus(amount);
        reserved = reserved.plus(amount);
        updatedAt = now;
    }

    /** Money leaves the payer: the hold is consumed. */
    public void captureReserved(Money amount, Instant now) {
        requirePositive(amount);
        if (amount.isGreaterThan(reserved)) {
            throw new DomainRuleViolationException("FUNDS_RESERVED_UNDERFLOW",
                    "Capture exceeds reserved funds on account " + accountId);
        }
        reserved = reserved.minus(amount);
        updatedAt = now;
    }

    /** Compensation: the hold is returned to available. */
    public void releaseReserved(Money amount, Instant now) {
        requirePositive(amount);
        if (amount.isGreaterThan(reserved)) {
            throw new DomainRuleViolationException("FUNDS_RESERVED_UNDERFLOW",
                    "Release exceeds reserved funds on account " + accountId);
        }
        reserved = reserved.minus(amount);
        available = available.plus(amount);
        updatedAt = now;
    }

    /** Incoming money (captured payment as payee, or deposit). */
    public void credit(Money amount, Instant now) {
        requirePositive(amount);
        available = available.plus(amount);
        updatedAt = now;
    }

    private void requirePositive(Money amount) {
        requireCurrency(amount, currency);
        if (!amount.isPositive()) {
            throw new DomainRuleViolationException("FUNDS_AMOUNT_NOT_POSITIVE", "Amount must be positive");
        }
    }

    private static Money requireCurrency(Money amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        if (!amount.hasCurrency(currency)) {
            throw new DomainRuleViolationException("FUNDS_CURRENCY_MISMATCH",
                    "Amount currency " + amount.currencyCode() + " does not match account currency " + currency);
        }
        return amount;
    }

    public AccountId accountId() {
        return accountId;
    }

    public Currency currency() {
        return currency;
    }

    public Money available() {
        return available;
    }

    public Money reserved() {
        return reserved;
    }

    public long version() {
        return version;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
