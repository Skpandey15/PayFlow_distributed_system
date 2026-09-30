package com.payflow.ledger.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Immutable in Hibernate as well as in the database: no dirty checking, no UPDATE ever generated. */
@Entity
@Immutable
@Table(schema = "ledger", name = "journal_entry")
public class JournalEntryJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100, updatable = false)
    private String reference;

    @Column(updatable = false)
    private String description;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt;

    @OneToMany(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "journal_entry_id", nullable = false, updatable = false)
    @OrderBy("lineNo")
    private List<LedgerEntryJpaEntity> lines = new ArrayList<>();

    protected JournalEntryJpaEntity() {
    }

    JournalEntryJpaEntity(UUID id, String reference, String description, String currency, Instant postedAt,
                          List<LedgerEntryJpaEntity> lines) {
        this.id = id;
        this.reference = reference;
        this.description = description;
        this.currency = currency;
        this.postedAt = postedAt;
        this.lines = new ArrayList<>(lines);
    }

    UUID getId() {
        return id;
    }

    String getReference() {
        return reference;
    }

    String getDescription() {
        return description;
    }

    Instant getPostedAt() {
        return postedAt;
    }

    List<LedgerEntryJpaEntity> getLines() {
        return lines;
    }
}
