package com.payflow.account.application.port.in;

import com.payflow.shared.domain.AccountId;

import java.util.Optional;

/**
 * Published query API of the Account context for <em>other bounded contexts</em>.
 * Other contexts must use this port and must never read {@code account.*} tables (enforced by ArchUnit).
 * When Account is extracted into its own service this interface becomes a remote API, so it is kept narrow.
 */
public interface LookupAccountUseCase {

    Optional<AccountSummary> findAccount(AccountId accountId);

    record AccountSummary(AccountId id, String ownerSubject, String currencyCode, boolean active) {
    }
}
