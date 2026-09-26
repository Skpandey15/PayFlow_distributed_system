package com.payflow.ledger.domain;

import com.payflow.shared.domain.AccountId;

import java.util.UUID;

/**
 * Internal (platform-owned) ledger accounts. Money deposited into customer accounts comes from outside
 * PayFlow; the other side of that double entry is this clearing account.
 */
public final class LedgerAccounts {

    public static final AccountId EXTERNAL_FUNDING_CLEARING =
            new AccountId(UUID.fromString("00000000-0000-7000-8000-00000000c1ea"));

    private LedgerAccounts() {
    }
}
