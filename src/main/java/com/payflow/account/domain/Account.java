package com.payflow.account.domain;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.DomainRuleViolationException;
import com.payflow.shared.domain.InvalidStateTransitionException;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/**
 * Account aggregate: who owns an account, which currency it is denominated in, and whether it may
 * participate in payments.
 *
 * <p>Deliberately thin. The account's <em>balance</em> is not stored here. It is derived from the
 * append-only double-entry ledger (Ledger context), which is the financial source of truth. This context
 * is close to master-data CRUD, and the WP-01 review records that heavier DDD tactics would add
 * ceremony without protecting any invariant here.
 */
public final class Account {

    public static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final AccountId id;
    private final String ownerSubject;
    private final String displayName;
    private final Currency currency;
    private final Instant createdAt;
    private final long version;
    private AccountStatus status;
    private Instant updatedAt;

    private Account(AccountSnapshot s) {
        this.id = Objects.requireNonNull(s.id(), "id");
        this.ownerSubject = Objects.requireNonNull(s.ownerSubject(), "ownerSubject");
        this.displayName = Objects.requireNonNull(s.displayName(), "displayName");
        this.currency = Objects.requireNonNull(s.currency(), "currency");
        this.status = Objects.requireNonNull(s.status(), "status");
        this.createdAt = Objects.requireNonNull(s.createdAt(), "createdAt");
        this.updatedAt = Objects.requireNonNull(s.updatedAt(), "updatedAt");
        this.version = s.version();
    }

    public static Account open(AccountId id, String ownerSubject, String displayName, Currency currency, Instant now) {
        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new DomainRuleViolationException("ACCOUNT_OWNER_REQUIRED", "Account owner is required");
        }
        if (displayName == null || displayName.isBlank() || displayName.strip().length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new DomainRuleViolationException("ACCOUNT_DISPLAY_NAME_INVALID",
                    "Display name must be 1-" + MAX_DISPLAY_NAME_LENGTH + " characters");
        }
        if (currency.getDefaultFractionDigits() < 0) {
            throw new DomainRuleViolationException("ACCOUNT_CURRENCY_UNSUPPORTED",
                    "Currency " + currency + " cannot hold monetary balances");
        }
        return new Account(new AccountSnapshot(id, ownerSubject, displayName.strip(), currency,
                AccountStatus.ACTIVE, now, now, 0L));
    }

    public static Account rehydrate(AccountSnapshot snapshot) {
        return new Account(snapshot);
    }

    public AccountSnapshot snapshot() {
        return new AccountSnapshot(id, ownerSubject, displayName, currency, status, createdAt, updatedAt, version);
    }

    public void freeze(Instant now) {
        if (status == AccountStatus.FROZEN) {
            throw new InvalidStateTransitionException("ACCOUNT_ALREADY_FROZEN", "Account " + id + " is already frozen");
        }
        status = AccountStatus.FROZEN;
        updatedAt = now;
    }

    public boolean isOwnedBy(String subject) {
        return ownerSubject.equals(subject);
    }

    public boolean canTransact() {
        return status == AccountStatus.ACTIVE;
    }

    public AccountId id() {
        return id;
    }

    public String ownerSubject() {
        return ownerSubject;
    }

    public String displayName() {
        return displayName;
    }

    public Currency currency() {
        return currency;
    }

    public AccountStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}
