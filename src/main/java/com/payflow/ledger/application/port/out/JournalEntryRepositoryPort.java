package com.payflow.ledger.application.port.out;

import com.payflow.ledger.domain.JournalEntry;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

import java.util.Currency;
import java.util.Optional;

public interface JournalEntryRepositoryPort {

    /**
     * Appends a journal entry.
     *
     * @throws DuplicateJournalReferenceException if an entry with the same reference was committed concurrently
     */
    void append(JournalEntry entry);

    Optional<JournalEntry> findByReference(String reference);

    Money balanceOf(AccountId accountId, Currency currency);

    class DuplicateJournalReferenceException extends RuntimeException {
        public DuplicateJournalReferenceException(String reference, Throwable cause) {
            super("Journal reference already exists: " + reference, cause);
        }
    }
}
