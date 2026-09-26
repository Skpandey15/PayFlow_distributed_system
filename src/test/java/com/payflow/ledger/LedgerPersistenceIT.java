package com.payflow.ledger;

import com.payflow.ledger.application.port.in.GetAccountBalanceUseCase;
import com.payflow.ledger.application.port.in.PostJournalEntryUseCase;
import com.payflow.ledger.application.port.in.PostJournalEntryUseCase.JournalEntryView;
import com.payflow.ledger.application.port.in.PostJournalEntryUseCase.PostJournalEntryCommand;
import com.payflow.ledger.application.port.in.PostJournalEntryUseCase.PostingLine;
import com.payflow.shared.application.ConflictException;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import com.payflow.support.Actors;
import com.payflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class LedgerPersistenceIT {

    @Autowired
    PostJournalEntryUseCase ledger;
    @Autowired
    GetAccountBalanceUseCase balances;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactionManager;

    final AccountId a = AccountId.newId();
    final AccountId b = AccountId.newId();

    PostJournalEntryCommand transfer(String reference, String amount) {
        return new PostJournalEntryCommand(reference, "test", List.of(
                new PostingLine(a, PostingLine.Side.DEBIT, Money.of(amount, "GBP")),
                new PostingLine(b, PostingLine.Side.CREDIT, Money.of(amount, "GBP"))));
    }

    @Test
    void balancesAreDerivedFromPostedLines() {
        ledger.post(transfer("t-" + UUID.randomUUID(), "30.00"));
        ledger.post(transfer("t-" + UUID.randomUUID(), "12.34"));

        assertThat(balances.balance(Actors.ledgerReader(), a, "GBP").balance()).isEqualTo(Money.of("-42.34", "GBP"));
        assertThat(balances.balance(Actors.ledgerReader(), b, "GBP").balance()).isEqualTo(Money.of("42.34", "GBP"));
        assertThat(balances.balance(Actors.ledgerReader(), b, "USD").balance().isZero()).isTrue();
    }

    @Test
    void postingIsIdempotentPerReference() {
        String reference = "payment:" + UUID.randomUUID() + ":settlement";
        JournalEntryView first = ledger.post(transfer(reference, "10.00"));
        JournalEntryView again = ledger.post(transfer(reference, "10.00"));

        assertThat(first.newlyPosted()).isTrue();
        assertThat(again.newlyPosted()).isFalse();
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(balances.balance(Actors.ledgerReader(), b, "GBP").balance()).isEqualTo(Money.of("10.00", "GBP"));
    }

    @Test
    void reusingAReferenceForDifferentLinesIsAConflict() {
        String reference = "ref-" + UUID.randomUUID();
        ledger.post(transfer(reference, "10.00"));
        assertThatThrownBy(() -> ledger.post(transfer(reference, "11.00")))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("LEDGER_REFERENCE_CONFLICT");
    }

    @Test
    void ledgerRowsCannotBeUpdatedOrDeleted() {
        String reference = "ref-" + UUID.randomUUID();
        ledger.post(transfer(reference, "5.00"));

        assertThatThrownBy(() -> jdbc.update("update ledger.ledger_entry set amount = 5000 where account_id = ?", b.value()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from ledger.journal_entry where reference = ?", reference))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void anUnbalancedJournalCannotBeCommittedEvenBypassingTheDomain() {
        UUID journalId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.update("insert into ledger.journal_entry (id, reference, currency, posted_at) values (?, ?, 'GBP', now())",
                    journalId, "raw-" + journalId);
            jdbc.update("""
                    insert into ledger.ledger_entry (id, journal_entry_id, line_no, account_id, direction, amount, currency)
                    values (?, ?, 1, ?, 'CREDIT', 1000000, 'GBP')""", UUID.randomUUID(), journalId, b.value());
        })).as("deferred constraint trigger fires at COMMIT").hasMessageContaining("unbalanced");

        assertThat(jdbc.queryForObject("select count(*) from ledger.journal_entry where id = ?", Integer.class, journalId))
                .isZero();
    }
}
