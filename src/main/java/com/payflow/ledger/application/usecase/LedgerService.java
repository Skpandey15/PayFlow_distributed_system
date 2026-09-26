package com.payflow.ledger.application.usecase;

import com.payflow.ledger.application.port.in.GetAccountBalanceUseCase;
import com.payflow.ledger.application.port.in.PostJournalEntryUseCase;
import com.payflow.ledger.application.port.out.JournalEntryRepositoryPort;
import com.payflow.ledger.application.port.out.JournalEntryRepositoryPort.DuplicateJournalReferenceException;
import com.payflow.ledger.domain.EntryDirection;
import com.payflow.ledger.domain.JournalEntry;
import com.payflow.ledger.domain.LedgerLine;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.ConflictException;
import com.payflow.shared.application.TransactionRunner;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

public class LedgerService implements PostJournalEntryUseCase, GetAccountBalanceUseCase {

    public static final String PERMISSION_READ = "ledger:read";

    private final JournalEntryRepositoryPort journal;
    private final TransactionRunner tx;
    private final Clock clock;

    public LedgerService(JournalEntryRepositoryPort journal, TransactionRunner tx, Clock clock) {
        this.journal = journal;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Transaction boundary: one journal entry (header + all lines) per transaction, so a partially
     * posted entry can never be observed. A concurrent duplicate loses on the unique reference
     * constraint and is resolved by returning the winner's entry.
     */
    @Override
    public JournalEntryView post(PostJournalEntryCommand command) {
        List<LedgerLine> lines = command.lines().stream().map(LedgerService::toDomain).toList();
        JournalEntry candidate = JournalEntry.post(command.reference(), command.description(), lines, clock.instant());
        try {
            return tx.inTransaction(() -> journal.findByReference(command.reference())
                    .map(existing -> sameEntryOrConflict(existing, candidate))
                    .orElseGet(() -> {
                        journal.append(candidate);
                        return view(candidate, true);
                    }));
        } catch (DuplicateJournalReferenceException raced) {
            JournalEntry winner = tx.readOnly(() -> journal.findByReference(command.reference())).orElseThrow();
            return sameEntryOrConflict(winner, candidate);
        }
    }

    @Override
    public BalanceView balance(Actor actor, AccountId accountId, String currencyCode) {
        requirePermission(actor, PERMISSION_READ);
        Money balance = tx.readOnly(() -> journal.balanceOf(accountId, Money.currency(currencyCode)));
        return new BalanceView(accountId, balance);
    }

    private static JournalEntryView sameEntryOrConflict(JournalEntry existing, JournalEntry requested) {
        if (!new HashSet<>(existing.lines()).equals(new HashSet<>(requested.lines()))) {
            throw new ConflictException("LEDGER_REFERENCE_CONFLICT",
                    "Journal reference " + existing.reference() + " was already posted with different lines");
        }
        return view(existing, false);
    }

    private static LedgerLine toDomain(PostingLine line) {
        EntryDirection direction = line.side() == PostingLine.Side.DEBIT ? EntryDirection.DEBIT : EntryDirection.CREDIT;
        return new LedgerLine(line.accountId(), direction, line.amount());
    }

    private static JournalEntryView view(JournalEntry entry, boolean newlyPosted) {
        List<PostingLine> lines = entry.lines().stream()
                .map(l -> new PostingLine(l.accountId(),
                        l.direction() == EntryDirection.DEBIT ? PostingLine.Side.DEBIT : PostingLine.Side.CREDIT,
                        l.amount()))
                .toList();
        return new JournalEntryView(entry.id(), entry.reference(), entry.currency().getCurrencyCode(),
                entry.postedAt(), lines, newlyPosted);
    }
}
