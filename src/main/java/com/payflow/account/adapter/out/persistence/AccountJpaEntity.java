package com.payflow.account.adapter.out.persistence;

import com.payflow.account.domain.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "account", name = "account")
public class AccountJpaEntity {

    @Id
    private UUID id;

    @Column(name = "owner_subject", nullable = false, updatable = false)
    private String ownerSubject;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountJpaEntity() {
    }

    AccountJpaEntity(UUID id, String ownerSubject, String displayName, String currency, AccountStatus status,
                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.ownerSubject = ownerSubject;
        this.displayName = displayName;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    void applyState(String displayName, AccountStatus status, Instant updatedAt) {
        this.displayName = displayName;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    String getOwnerSubject() {
        return ownerSubject;
    }

    String getDisplayName() {
        return displayName;
    }

    String getCurrency() {
        return currency;
    }

    AccountStatus getStatus() {
        return status;
    }

    Long getVersion() {
        return version;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
