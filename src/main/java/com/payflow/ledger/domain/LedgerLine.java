package com.payflow.ledger.domain;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.DomainRuleViolationException;
import com.payflow.shared.domain.Money;

import java.util.Objects;

/** One side of a journal entry: a strictly positive amount debited or credited to one account. */
public record LedgerLine(AccountId accountId, EntryDirection direction, Money amount) {

    public LedgerLine {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new DomainRuleViolationException("LEDGER_LINE_AMOUNT_NOT_POSITIVE",
                    "Ledger line amounts must be positive; direction carries the sign");
        }
    }

    public static LedgerLine debit(AccountId accountId, Money amount) {
        return new LedgerLine(accountId, EntryDirection.DEBIT, amount);
    }

    public static LedgerLine credit(AccountId accountId, Money amount) {
        return new LedgerLine(accountId, EntryDirection.CREDIT, amount);
    }

    /** Effect on the account balance under the liability-account convention (credit +, debit -). */
    public Money signedAmount() {
        return direction == EntryDirection.CREDIT ? amount : amount.negate();
    }
}
