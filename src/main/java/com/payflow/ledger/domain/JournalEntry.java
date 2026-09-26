package com.payflow.ledger.domain;

import com.payflow.shared.domain.DomainRuleViolationException;
import com.payflow.shared.domain.Identifiers;
import com.payflow.shared.domain.Money;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Journal entry aggregate: an atomic, immutable, balanced set of ledger lines.
 *
 * <p>Invariants:
 * <ul>
 *   <li>at least two lines, all in one currency (no implicit FX)</li>
 *   <li>sum(debits) == sum(credits), the fundamental double-entry rule</li>
 *   <li>a unique business {@code reference} makes posting idempotent (e.g. {@code payment:<id>:settlement})</li>
 *   <li>append-only: there are no mutators. Corrections are new compensating entries, never updates.</li>
 * </ul>
 * The balance rule is also enforced by a deferred PostgreSQL constraint trigger, and immutability by an
 * UPDATE/DELETE-rejecting trigger (defence in depth against code paths that bypass this class).
 */
public final class JournalEntry {

    public static final int MAX_REFERENCE_LENGTH = 100;

    private final UUID id;
    private final String reference;
    private final String description;
    private final Currency currency;
    private final List<LedgerLine> lines;
    private final Instant postedAt;

    private JournalEntry(UUID id, String reference, String description, Currency currency,
                         List<LedgerLine> lines, Instant postedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.reference = reference;
        this.description = description;
        this.currency = currency;
        this.lines = List.copyOf(lines);
        this.postedAt = Objects.requireNonNull(postedAt, "postedAt");
    }

    public static JournalEntry post(String reference, String description, List<LedgerLine> lines, Instant now) {
        validate(reference, lines);
        return new JournalEntry(Identifiers.timeOrderedUuid(), reference, description,
                lines.getFirst().amount().currency(), lines, now);
    }

    public static JournalEntry rehydrate(UUID id, String reference, String description,
                                         List<LedgerLine> lines, Instant postedAt) {
        validate(reference, lines);
        return new JournalEntry(id, reference, description, lines.getFirst().amount().currency(), lines, postedAt);
    }

    private static void validate(String reference, List<LedgerLine> lines) {
        if (reference == null || reference.isBlank() || reference.length() > MAX_REFERENCE_LENGTH) {
            throw new DomainRuleViolationException("LEDGER_REFERENCE_INVALID",
                    "Journal reference must be 1-" + MAX_REFERENCE_LENGTH + " characters");
        }
        if (lines == null || lines.size() < 2) {
            throw new DomainRuleViolationException("LEDGER_TOO_FEW_LINES", "A journal entry needs at least two lines");
        }
        Currency currency = lines.getFirst().amount().currency();
        Money debits = Money.zero(currency);
        Money credits = Money.zero(currency);
        for (LedgerLine line : lines) {
            if (!line.amount().hasCurrency(currency)) {
                throw new DomainRuleViolationException("LEDGER_MIXED_CURRENCY",
                        "All lines of a journal entry must share one currency");
            }
            if (line.direction() == EntryDirection.DEBIT) {
                debits = debits.plus(line.amount());
            } else {
                credits = credits.plus(line.amount());
            }
        }
        if (!debits.equals(credits)) {
            throw new DomainRuleViolationException("LEDGER_UNBALANCED",
                    "Debits %s do not equal credits %s".formatted(debits, credits));
        }
    }

    public UUID id() {
        return id;
    }

    public String reference() {
        return reference;
    }

    public String description() {
        return description;
    }

    public Currency currency() {
        return currency;
    }

    public List<LedgerLine> lines() {
        return lines;
    }

    public Instant postedAt() {
        return postedAt;
    }
}
