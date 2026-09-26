package com.payflow.account.domain;

import com.payflow.shared.domain.AccountId;

import java.time.Instant;
import java.util.Currency;

public record AccountSnapshot(
        AccountId id,
        String ownerSubject,
        String displayName,
        Currency currency,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
