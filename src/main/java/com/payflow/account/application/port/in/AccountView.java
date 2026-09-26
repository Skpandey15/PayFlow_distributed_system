package com.payflow.account.application.port.in;

import com.payflow.account.domain.Account;

import java.time.Instant;
import java.util.UUID;

/** Read model returned by account use cases. */
public record AccountView(UUID id, String ownerSubject, String displayName, String currency, String status,
                          Instant createdAt, Instant updatedAt) {

    public static AccountView from(Account account) {
        return new AccountView(account.id().value(), account.ownerSubject(), account.displayName(),
                account.currency().getCurrencyCode(), account.status().name(), account.createdAt(),
                account.updatedAt());
    }
}
