package com.payflow.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of an account. Part of the shared kernel because Payment, Ledger and Account all refer to
 * accounts by identity. No context shares account <em>state</em>, only this identifier.
 */
public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AccountId newId() {
        return new AccountId(Identifiers.timeOrderedUuid());
    }

    public static AccountId of(String value) {
        return new AccountId(Identifiers.parse(value, "account id"));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
