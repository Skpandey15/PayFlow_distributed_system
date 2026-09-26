package com.payflow.account.domain;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.InvalidStateTransitionException;
import com.payflow.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    final Instant now = Instant.parse("2026-09-01T10:00:00Z");

    @Test
    void openedAccountsAreActiveAndOwned() {
        Account account = Account.open(AccountId.newId(), "alice", "  Main  ", Money.currency("EUR"), now);
        assertThat(account.canTransact()).isTrue();
        assertThat(account.isOwnedBy("alice")).isTrue();
        assertThat(account.isOwnedBy("bob")).isFalse();
        assertThat(account.displayName()).isEqualTo("Main");
    }

    @Test
    void frozenAccountsCannotTransactAndCannotBeFrozenTwice() {
        Account account = Account.open(AccountId.newId(), "alice", "Main", Money.currency("EUR"), now);
        account.freeze(now);
        assertThat(account.canTransact()).isFalse();
        assertThatThrownBy(() -> account.freeze(now)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void validatesOwnerAndName() {
        assertThatThrownBy(() -> Account.open(AccountId.newId(), " ", "Main", Money.currency("EUR"), now))
                .extracting("code").isEqualTo("ACCOUNT_OWNER_REQUIRED");
        assertThatThrownBy(() -> Account.open(AccountId.newId(), "alice", "x".repeat(101), Money.currency("EUR"), now))
                .extracting("code").isEqualTo("ACCOUNT_DISPLAY_NAME_INVALID");
    }
}
