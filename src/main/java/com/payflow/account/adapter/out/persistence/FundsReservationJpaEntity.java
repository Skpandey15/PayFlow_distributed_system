package com.payflow.account.adapter.out.persistence;

import com.payflow.account.domain.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "account", name = "funds_reservation")
public class FundsReservationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "payer_account_id", nullable = false, updatable = false)
    private UUID payerAccountId;

    @Column(name = "payee_account_id", updatable = false)
    private UUID payeeAccountId;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column
    private String reason;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FundsReservationJpaEntity() {
    }

    FundsReservationJpaEntity(UUID id, UUID paymentId, UUID payerAccountId, UUID payeeAccountId, BigDecimal amount,
                              String currency, ReservationStatus status, String reason, Instant createdAt,
                              Instant updatedAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.payerAccountId = payerAccountId;
        this.payeeAccountId = payeeAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    void apply(ReservationStatus status, String reason, Instant updatedAt) {
        this.status = status;
        this.reason = reason;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getPaymentId() {
        return paymentId;
    }

    UUID getPayerAccountId() {
        return payerAccountId;
    }

    UUID getPayeeAccountId() {
        return payeeAccountId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    ReservationStatus getStatus() {
        return status;
    }

    String getReason() {
        return reason;
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
