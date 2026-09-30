package com.payflow.payment.adapter.out.persistence;

import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.domain.PaymentStatus;
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

/**
 * Persistence representation of a payment. Lives only in the adapter: the domain {@link
 * com.payflow.payment.domain.Payment} carries no JPA annotations and is mapped by {@link PaymentPersistenceMapper}.
 */
@Entity
@Table(schema = "payment", name = "payment")
public class PaymentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "payer_account_id", nullable = false, updatable = false)
    private UUID payerAccountId;

    @Column(name = "payee_account_id", nullable = false, updatable = false)
    private UUID payeeAccountId;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 140, updatable = false)
    private String reference;

    @Column(name = "initiated_by", nullable = false, updatable = false)
    private String initiatedBy;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Optimistic-locking token. Hibernate issues {@code UPDATE ... WHERE id=? AND version=?} and increments it. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentJpaEntity() {
    }

    PaymentJpaEntity(UUID id, UUID payerAccountId, UUID payeeAccountId, BigDecimal amount, String currency,
                     PaymentMethod method, String reference, String initiatedBy, Instant createdAt) {
        this.id = id;
        this.payerAccountId = payerAccountId;
        this.payeeAccountId = payeeAccountId;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.reference = reference;
        this.initiatedBy = initiatedBy;
        this.createdAt = createdAt;
    }

    void applyLifecycleState(PaymentStatus status, String failureReason, Instant updatedAt) {
        this.status = status;
        this.failureReason = failureReason;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
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

    PaymentMethod getMethod() {
        return method;
    }

    PaymentStatus getStatus() {
        return status;
    }

    String getReference() {
        return reference;
    }

    String getInitiatedBy() {
        return initiatedBy;
    }

    String getFailureReason() {
        return failureReason;
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
