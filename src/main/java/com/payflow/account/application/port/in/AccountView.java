package com.payflow.account.application.port.in;

import com.payflow.account.domain.Account;
import com.payflow.account.domain.AccountBalance;

import java.time.Instant;
import java.util.UUID;

/** Read model returned by account use cases, including spendable (available) and held (reserved) funds. */
public record AccountView(UUID id, String ownerSubject, String displayName, String currency, String status,
                          String availableBalance, String reservedBalance, Instant createdAt, Instant updatedAt) {

    public static AccountView from(Account account, AccountBalance balance) {
        return new AccountView(account.id().value(), account.ownerSubject(), account.displayName(),
                account.currency().getCurrencyCode(), account.status().name(),
                balance.available().amount().toPlainString(), balance.reserved().amount().toPlainString(),
                account.createdAt(), account.updatedAt());
    }
}
