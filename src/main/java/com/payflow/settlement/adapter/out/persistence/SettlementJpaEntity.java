package com.payflow.settlement.adapter.out.persistence;

import com.payflow.settlement.domain.SettlementRail;
import com.payflow.settlement.domain.SettlementSnapshot;
import com.payflow.shared.domain.Money;
import com.payflow.settlement.domain.SettlementStatus;
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
@Table(schema = "settlement", name = "settlement")
public class SettlementJpaEntity {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private SettlementRail rail;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "payment_reference", length = 140, updatable = false)
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "submission_attempts", nullable = false)
    private int submissionAttempts;

    @Column(name = "last_attempt_outcome", length = 20)
    private String lastAttemptOutcome;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SettlementJpaEntity() {
    }

    SettlementJpaEntity(UUID id, UUID paymentId, SettlementRail rail, BigDecimal amount, String currency,
                        String paymentReference, Instant createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.rail = rail;
        this.amount = amount;
        this.currency = currency;
        this.paymentReference = paymentReference;
        this.createdAt = createdAt;
    }

    void applyState(SettlementSnapshot s) {
        this.status = s.status();
        this.providerReference = s.providerReference();
        this.declineReason = s.declineReason();
        this.submissionAttempts = s.submissionAttempts();
        this.lastAttemptOutcome = s.lastAttemptOutcome();
        this.lastErrorCode = s.lastErrorCode();
        this.lastAttemptAt = s.lastAttemptAt();
        this.updatedAt = s.updatedAt();
    }

    SettlementSnapshot toSnapshot() {
        return new SettlementSnapshot(id, paymentId, rail, Money.of(amount, Money.currency(currency)), paymentReference,
                status, providerReference, declineReason, submissionAttempts, lastAttemptOutcome, lastErrorCode,
                lastAttemptAt, createdAt, updatedAt, version);
    }

    UUID getId() {
        return id;
    }

    UUID getPaymentId() {
        return paymentId;
    }

    SettlementRail getRail() {
        return rail;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    String getPaymentReference() {
        return paymentReference;
    }

    SettlementStatus getStatus() {
        return status;
    }

    String getProviderReference() {
        return providerReference;
    }

    String getDeclineReason() {
        return declineReason;
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
