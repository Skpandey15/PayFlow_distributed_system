package com.payflow.account.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "account", name = "account_balance")
public class AccountBalanceJpaEntity {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal available;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal reserved;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountBalanceJpaEntity() {
    }

    AccountBalanceJpaEntity(UUID accountId, String currency, BigDecimal available, BigDecimal reserved, Instant updatedAt) {
        this.accountId = accountId;
        this.currency = currency;
        this.available = available;
        this.reserved = reserved;
        this.updatedAt = updatedAt;
    }

    void apply(BigDecimal available, BigDecimal reserved, Instant updatedAt) {
        this.available = available;
        this.reserved = reserved;
        this.updatedAt = updatedAt;
    }

    UUID getAccountId() {
        return accountId;
    }

    String getCurrency() {
        return currency;
    }

    BigDecimal getAvailable() {
        return available;
    }

    BigDecimal getReserved() {
        return reserved;
    }

    Long getVersion() {
        return version;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
