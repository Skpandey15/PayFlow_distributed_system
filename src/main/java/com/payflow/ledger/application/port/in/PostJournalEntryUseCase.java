package com.payflow.ledger.application.port.in;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published command API of the Ledger context. Posting is idempotent on {@code reference}. Re-posting
 * the same reference with identical lines returns the existing entry, and with different lines is a
 * conflict. That makes the call safe to retry after a crash or timeout.
 */
public interface PostJournalEntryUseCase {

    JournalEntryView post(PostJournalEntryCommand command);

    record PostJournalEntryCommand(String reference, String description, List<PostingLine> lines) {
        public PostJournalEntryCommand {
            lines = List.copyOf(lines);
        }
    }

    record PostingLine(AccountId accountId, Side side, Money amount) {
        public enum Side { DEBIT, CREDIT }
    }

    record JournalEntryView(UUID id, String reference, String currency, Instant postedAt, List<PostingLine> lines,
                            boolean newlyPosted) {
    }
}
