package com.payflow.settlement.domain;

import com.payflow.shared.domain.DomainRuleViolationException;
import com.payflow.shared.domain.Identifiers;
import com.payflow.shared.domain.InvalidStateTransitionException;
import com.payflow.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Settlement aggregate: our record of an instruction sent to an external settlement rail for one payment.
 *
 * <p>Exactly one settlement exists per payment (unique {@code paymentId}). The payment id doubles as the
 * idempotency key sent to the provider, so a resumed or duplicated submission cannot move money twice.
 */
public final class Settlement {

    private final UUID id;
    private final UUID paymentId;
    private final SettlementRail rail;
    private final Money amount;
    private final String paymentReference;
    private final Instant createdAt;
    private final long version;
    private SettlementStatus status;
    private String providerReference;
    private String declineReason;
    private int submissionAttempts;
    private String lastAttemptOutcome;
    private String lastErrorCode;
    private Instant lastAttemptAt;
    private Instant updatedAt;

    private Settlement(SettlementSnapshot s) {
        this.id = Objects.requireNonNull(s.id(), "id");
        this.paymentId = Objects.requireNonNull(s.paymentId(), "paymentId");
        this.rail = Objects.requireNonNull(s.rail(), "rail");
        this.amount = Objects.requireNonNull(s.amount(), "amount");
        this.paymentReference = s.paymentReference();
        this.status = Objects.requireNonNull(s.status(), "status");
        this.providerReference = s.providerReference();
        this.declineReason = s.declineReason();
        this.submissionAttempts = s.submissionAttempts();
        this.lastAttemptOutcome = s.lastAttemptOutcome();
        this.lastErrorCode = s.lastErrorCode();
        this.lastAttemptAt = s.lastAttemptAt();
        this.createdAt = Objects.requireNonNull(s.createdAt(), "createdAt");
        this.updatedAt = Objects.requireNonNull(s.updatedAt(), "updatedAt");
        this.version = s.version();
    }

    public static Settlement initiate(UUID paymentId, SettlementRail rail, Money amount, String paymentReference,
                                      Instant now) {
        if (!amount.isPositive()) {
            throw new DomainRuleViolationException("SETTLEMENT_AMOUNT_NOT_POSITIVE", "Settlement amount must be positive");
        }
        return new Settlement(new SettlementSnapshot(Identifiers.timeOrderedUuid(), paymentId, rail, amount,
                paymentReference, SettlementStatus.PENDING, null, null, 0, null, null, null, now, now, 0L));
    }

    public static Settlement rehydrate(SettlementSnapshot snapshot) {
        return new Settlement(snapshot);
    }

    public SettlementSnapshot snapshot() {
        return new SettlementSnapshot(id, paymentId, rail, amount, paymentReference, status, providerReference,
                declineReason, submissionAttempts, lastAttemptOutcome, lastErrorCode, lastAttemptAt, createdAt, updatedAt,
                version);
    }

    public void complete(String providerReference, Instant now) {
        requirePending();
        if (providerReference == null || providerReference.isBlank()) {
            throw new DomainRuleViolationException("SETTLEMENT_PROVIDER_REFERENCE_REQUIRED",
                    "A completed settlement must carry the provider reference");
        }
        this.status = SettlementStatus.COMPLETED;
        this.providerReference = providerReference;
        answered(now);
    }

    public void decline(String reason, Instant now) {
        requirePending();
        this.status = SettlementStatus.DECLINED;
        this.declineReason = (reason == null || reason.isBlank()) ? "DECLINED_BY_RAIL" : reason;
        answered(now);
    }

    /**
     * A submission got no answer. The settlement stays PENDING (resumable with the same idempotency key); what we
     * record is the evidence an operator needs later: did the instruction provably not leave (NOT_SENT) or may the
     * rail have processed it (UNKNOWN)?
     */
    public void recordUnansweredAttempt(String deliveryOutcome, String errorCode, Instant now) {
        requirePending();
        submissionAttempts++;
        lastAttemptOutcome = deliveryOutcome;
        lastErrorCode = errorCode;
        lastAttemptAt = now;
        updatedAt = now;
    }

    private void answered(Instant now) {
        submissionAttempts++;
        lastAttemptOutcome = "ANSWERED";
        lastErrorCode = null;
        lastAttemptAt = now;
        updatedAt = now;
    }

    private void requirePending() {
        if (status != SettlementStatus.PENDING) {
            throw new InvalidStateTransitionException("SETTLEMENT_NOT_PENDING",
                    "Settlement %s is already %s".formatted(id, status));
        }
    }

    public UUID id() {
        return id;
    }

    public UUID paymentId() {
        return paymentId;
    }

    public SettlementRail rail() {
        return rail;
    }

    public Money amount() {
        return amount;
    }

    public String paymentReference() {
        return paymentReference;
    }

    public SettlementStatus status() {
        return status;
    }

    public String providerReference() {
        return providerReference;
    }

    public String declineReason() {
        return declineReason;
    }

    public long version() {
        return version;
    }

    public int submissionAttempts() {
        return submissionAttempts;
    }

    public String lastAttemptOutcome() {
        return lastAttemptOutcome;
    }

    public String lastErrorCode() {
        return lastErrorCode;
    }

    public Instant lastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
