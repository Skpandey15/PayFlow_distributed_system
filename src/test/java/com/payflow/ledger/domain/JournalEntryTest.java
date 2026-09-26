package com.payflow.ledger.domain;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.DomainRuleViolationException;
import com.payflow.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalEntryTest {

    final AccountId a = AccountId.newId();
    final AccountId b = AccountId.newId();
    final AccountId c = AccountId.newId();
    final Instant now = Instant.parse("2026-09-01T10:00:00Z");

    @Test
    void balancedEntryIsAccepted() {
        JournalEntry entry = JournalEntry.post("payment:1:settlement", "test", List.of(
                LedgerLine.debit(a, Money.of("100.00", "USD")),
                LedgerLine.credit(b, Money.of("60.00", "USD")),
                LedgerLine.credit(c, Money.of("40.00", "USD"))), now);
        assertThat(entry.lines()).hasSize(3);
        assertThat(entry.currency().getCurrencyCode()).isEqualTo("USD");
        Money net = entry.lines().stream().map(LedgerLine::signedAmount).reduce(Money.zero(entry.currency()), Money::plus);
        assertThat(net.isZero()).as("double-entry: net effect of a journal entry is zero").isTrue();
    }

    @Test
    void unbalancedEntryIsRejected() {
        assertThatThrownBy(() -> JournalEntry.post("ref", null, List.of(
                LedgerLine.debit(a, Money.of("100.00", "USD")),
                LedgerLine.credit(b, Money.of("99.99", "USD"))), now))
                .isInstanceOf(DomainRuleViolationException.class)
                .extracting("code").isEqualTo("LEDGER_UNBALANCED");
    }

    @Test
    void mixedCurrenciesAreRejected() {
        assertThatThrownBy(() -> JournalEntry.post("ref", null, List.of(
                LedgerLine.debit(a, Money.of("100.00", "USD")),
                LedgerLine.credit(b, Money.of("100.00", "EUR"))), now))
                .extracting("code").isEqualTo("LEDGER_MIXED_CURRENCY");
    }

    @Test
    void singleLineAndNonPositiveLinesAreRejected() {
        assertThatThrownBy(() -> JournalEntry.post("ref", null, List.of(LedgerLine.debit(a, Money.of("1", "USD"))), now))
                .extracting("code").isEqualTo("LEDGER_TOO_FEW_LINES");
        assertThatThrownBy(() -> LedgerLine.credit(a, Money.of("-1", "USD")))
                .extracting("code").isEqualTo("LEDGER_LINE_AMOUNT_NOT_POSITIVE");
    }

    @Test
    void referenceIsMandatory() {
        assertThatThrownBy(() -> JournalEntry.post(" ", null, List.of(
                LedgerLine.debit(a, Money.of("1", "USD")), LedgerLine.credit(b, Money.of("1", "USD"))), now))
                .extracting("code").isEqualTo("LEDGER_REFERENCE_INVALID");
    }
}
