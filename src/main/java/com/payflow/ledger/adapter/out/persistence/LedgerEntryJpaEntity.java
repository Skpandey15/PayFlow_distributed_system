package com.payflow.ledger.adapter.out.persistence;

import com.payflow.ledger.domain.EntryDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Immutable
@Table(schema = "ledger", name = "ledger_entry")
public class LedgerEntryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "line_no", nullable = false, updatable = false)
    private short lineNo;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6, updatable = false)
    private EntryDirection direction;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    protected LedgerEntryJpaEntity() {
    }

    LedgerEntryJpaEntity(UUID id, short lineNo, UUID accountId, EntryDirection direction, BigDecimal amount,
                         String currency) {
        this.id = id;
        this.lineNo = lineNo;
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
    }

    UUID getAccountId() {
        return accountId;
    }

    EntryDirection getDirection() {
        return direction;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }
}
