package com.payflow.payment.adapter.out.persistence;

import com.payflow.payment.domain.saga.CompensationReason;
import com.payflow.payment.domain.saga.SagaStep;
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
@Table(schema = "payment", name = "payment_saga")
public class PaymentSagaJpaEntity {

    @Id
    @Column(name = "saga_id")
    private UUID sagaId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SagaStep step;

    @Enumerated(EnumType.STRING)
    @Column(name = "compensation_reason", length = 30)
    private CompensationReason compensationReason;

    @Column(name = "outcome_reason")
    private String outcomeReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalated_from", length = 30)
    private SagaStep escalatedFrom;

    @Column(name = "step_attempts", nullable = false)
    private int stepAttempts;

    @Column(name = "step_started_at", nullable = false)
    private Instant stepStartedAt;

    @Column(name = "correlation_id", nullable = false, length = 64, updatable = false)
    private String correlationId;

    @Column(name = "checkout_device_id", length = 128, updatable = false)
    private String checkoutDeviceId;

    @Column(name = "checkout_ip_address", length = 45, updatable = false)
    private String checkoutIpAddress;

    @Column(name = "checkout_user_agent", length = 512, updatable = false)
    private String checkoutUserAgent;

    @Column(name = "checkout_country", length = 2, updatable = false)
    private String checkoutCountry;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentSagaJpaEntity() {
    }

    PaymentSagaJpaEntity(UUID sagaId, UUID paymentId, String correlationId, String checkoutDeviceId,
                         String checkoutIpAddress, String checkoutUserAgent, String checkoutCountry, Instant createdAt) {
        this.sagaId = sagaId;
        this.paymentId = paymentId;
        this.correlationId = correlationId;
        this.checkoutDeviceId = checkoutDeviceId;
        this.checkoutIpAddress = checkoutIpAddress;
        this.checkoutUserAgent = checkoutUserAgent;
        this.checkoutCountry = checkoutCountry;
        this.createdAt = createdAt;
    }

    void apply(SagaStep step, CompensationReason compensationReason, String outcomeReason, int stepAttempts,
               Instant stepStartedAt, Instant updatedAt, SagaStep escalatedFrom) {
        this.escalatedFrom = escalatedFrom;
        this.step = step;
        this.compensationReason = compensationReason;
        this.outcomeReason = outcomeReason;
        this.stepAttempts = stepAttempts;
        this.stepStartedAt = stepStartedAt;
        this.updatedAt = updatedAt;
    }

    SagaStep getEscalatedFrom() {
        return escalatedFrom;
    }

    UUID getSagaId() {
        return sagaId;
    }

    UUID getPaymentId() {
        return paymentId;
    }

    SagaStep getStep() {
        return step;
    }

    CompensationReason getCompensationReason() {
        return compensationReason;
    }

    String getOutcomeReason() {
        return outcomeReason;
    }

    int getStepAttempts() {
        return stepAttempts;
    }

    Instant getStepStartedAt() {
        return stepStartedAt;
    }

    String getCorrelationId() {
        return correlationId;
    }

    String getCheckoutDeviceId() {
        return checkoutDeviceId;
    }

    String getCheckoutIpAddress() {
        return checkoutIpAddress;
    }

    String getCheckoutUserAgent() {
        return checkoutUserAgent;
    }

    String getCheckoutCountry() {
        return checkoutCountry;
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
