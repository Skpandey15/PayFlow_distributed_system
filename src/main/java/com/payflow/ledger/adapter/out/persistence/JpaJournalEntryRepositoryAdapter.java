package com.payflow.ledger.adapter.out.persistence;

import com.payflow.ledger.application.port.out.JournalEntryRepositoryPort;
import com.payflow.ledger.domain.JournalEntry;
import com.payflow.ledger.domain.LedgerLine;
import com.payflow.platform.persistence.PersistenceErrors;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Identifiers;
import com.payflow.shared.domain.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

@Component
class JpaJournalEntryRepositoryAdapter implements JournalEntryRepositoryPort {

    static final String REFERENCE_CONSTRAINT = "uq_journal_entry_reference";

    private final EntityManager entityManager;

    JpaJournalEntryRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void append(JournalEntry entry) {
        List<LedgerEntryJpaEntity> lines = new ArrayList<>();
        short lineNo = 1;
        for (LedgerLine line : entry.lines()) {
            lines.add(new LedgerEntryJpaEntity(Identifiers.timeOrderedUuid(), lineNo++, line.accountId().value(),
                    line.direction(), line.amount().amount(), line.amount().currencyCode()));
        }
        JournalEntryJpaEntity entity = new JournalEntryJpaEntity(entry.id(), entry.reference(), entry.description(),
                entry.currency().getCurrencyCode(), entry.postedAt(), lines);
        try {
            entityManager.persist(entity);
            entityManager.flush();
        } catch (PersistenceException e) {
            if (PersistenceErrors.isUniqueViolation(e, REFERENCE_CONSTRAINT)) {
                throw new DuplicateJournalReferenceException(entry.reference(), e);
            }
            throw e;
        }
    }

    @Override
    public Optional<JournalEntry> findByReference(String reference) {
        return entityManager.createQuery("""
                        select distinct j from JournalEntryJpaEntity j
                        left join fetch j.lines
                        where j.reference = :reference""", JournalEntryJpaEntity.class)
                .setParameter("reference", reference)
                .getResultStream()
                .findFirst()
                .map(JpaJournalEntryRepositoryAdapter::toDomain);
    }

    /**
     * Balance is derived by aggregation, never stored. That is correct by construction (no lost updates on a
     * balance row) at the cost of O(lines) per query. The WP-01 review records periodic balance snapshots
     * as the scaling path once per-account line counts grow.
     */
    @Override
    public Money balanceOf(AccountId accountId, Currency currency) {
        BigDecimal sum = (BigDecimal) entityManager.createNativeQuery("""
                        select coalesce(sum(case direction when 'CREDIT' then amount else -amount end), 0)
                          from ledger.ledger_entry
                         where account_id = :accountId and currency = :currency""")
                .setParameter("accountId", accountId.value())
                .setParameter("currency", currency.getCurrencyCode())
                .getSingleResult();
        return Money.of(sum, currency);
    }

    private static JournalEntry toDomain(JournalEntryJpaEntity e) {
        List<LedgerLine> lines = e.getLines().stream()
                .map(l -> new LedgerLine(new AccountId(l.getAccountId()), l.getDirection(),
                        Money.of(l.getAmount(), Money.currency(l.getCurrency()))))
                .toList();
        return JournalEntry.rehydrate(e.getId(), e.getReference(), e.getDescription(), lines, e.getPostedAt());
    }
}
