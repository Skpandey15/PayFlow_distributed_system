package com.payflow.payment.domain;

import com.payflow.payment.domain.PaymentEvent.PaymentAuthorized;
import com.payflow.payment.domain.PaymentEvent.PaymentCancelled;
import com.payflow.payment.domain.PaymentEvent.PaymentCreated;
import com.payflow.payment.domain.PaymentEvent.PaymentFailed;
import com.payflow.payment.domain.PaymentEvent.PaymentProcessingStarted;
import com.payflow.payment.domain.PaymentEvent.PaymentRejected;
import com.payflow.payment.domain.PaymentEvent.PaymentSettled;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.DomainRuleViolationException;
import com.payflow.shared.domain.InvalidStateTransitionException;
import com.payflow.shared.domain.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Payment aggregate root: the consistency boundary for one payment's lifecycle.
 *
 * <p>Invariants protected here (not in controllers, not in the database alone):
 * <ul>
 *   <li>amount is strictly positive and correctly scaled for its currency ({@link Money})</li>
 *   <li>payer and payee are different accounts</li>
 *   <li>status only moves along {@link PaymentStatus}'s allowed transitions</li>
 *   <li>REJECTED/FAILED always carry a reason</li>
 *   <li>identity, parties, amount and method are immutable after creation; only lifecycle state changes</li>
 * </ul>
 * Concurrency across transactions is <em>not</em> an in-memory concern. It is enforced by the
 * repository through the {@link #version()} optimistic-locking token.
 */
public final class Payment {

    public static final int MAX_REFERENCE_LENGTH = 140;
    public static final int MAX_REASON_LENGTH = 255;

    private final PaymentId id;
    private final AccountId payerAccountId;
    private final AccountId payeeAccountId;
    private final Money amount;
    private final PaymentMethod method;
    private final String reference;
    private final String initiatedBy;
    private final Instant createdAt;
    private final long version;
    private PaymentStatus status;
    private String failureReason;
    private Instant updatedAt;
    private final List<PaymentEvent> pendingEvents = new ArrayList<>();

    private Payment(PaymentSnapshot s) {
        this.id = Objects.requireNonNull(s.id(), "id");
        this.payerAccountId = Objects.requireNonNull(s.payerAccountId(), "payerAccountId");
        this.payeeAccountId = Objects.requireNonNull(s.payeeAccountId(), "payeeAccountId");
        this.amount = Objects.requireNonNull(s.amount(), "amount");
        this.method = Objects.requireNonNull(s.method(), "method");
        this.reference = s.reference();
        this.initiatedBy = Objects.requireNonNull(s.initiatedBy(), "initiatedBy");
        this.status = Objects.requireNonNull(s.status(), "status");
        this.failureReason = s.failureReason();
        this.createdAt = Objects.requireNonNull(s.createdAt(), "createdAt");
        this.updatedAt = Objects.requireNonNull(s.updatedAt(), "updatedAt");
        this.version = s.version();
    }

    /** Factory for a brand-new payment. Validates creation invariants and records {@link PaymentCreated}. */
    public static Payment initiate(PaymentId id, AccountId payer, AccountId payee, Money amount,
                                   PaymentMethod method, String reference, String initiatedBy, Instant now) {
        Objects.requireNonNull(payer, "payer");
        Objects.requireNonNull(payee, "payee");
        Objects.requireNonNull(amount, "amount");
        if (payer.equals(payee)) {
            throw new DomainRuleViolationException("PAYMENT_SAME_ACCOUNT",
                    "Payer and payee must be different accounts");
        }
        if (!amount.isPositive()) {
            throw new DomainRuleViolationException("PAYMENT_AMOUNT_NOT_POSITIVE",
                    "Payment amount must be greater than zero");
        }
        if (initiatedBy == null || initiatedBy.isBlank()) {
            throw new DomainRuleViolationException("PAYMENT_INITIATOR_REQUIRED", "Payment initiator is required");
        }
        Payment payment = new Payment(new PaymentSnapshot(id, payer, payee, amount, method,
                normaliseReference(reference), initiatedBy, PaymentStatus.CREATED, null, now, now, 0L));
        payment.record(new PaymentCreated(id, payer, payee, amount, method, now));
        return payment;
    }

    /** Reconstitutes a persisted payment. No invariants are re-announced and no events are recorded. */
    public static Payment rehydrate(PaymentSnapshot snapshot) {
        return new Payment(snapshot);
    }

    public PaymentSnapshot snapshot() {
        return new PaymentSnapshot(id, payerAccountId, payeeAccountId, amount, method, reference, initiatedBy,
                status, failureReason, createdAt, updatedAt, version);
    }

    public void authorize(Instant now) {
        transitionTo(PaymentStatus.AUTHORIZED, now);
        record(new PaymentAuthorized(id, now));
    }

    public void reject(String reason, Instant now) {
        String checkedReason = requireReason(reason);
        transitionTo(PaymentStatus.REJECTED, now);
        this.failureReason = checkedReason;
        record(new PaymentRejected(id, checkedReason, now));
    }

    public void cancel(Instant now) {
        transitionTo(PaymentStatus.CANCELLED, now);
        record(new PaymentCancelled(id, now));
    }

    public void startProcessing(Instant now) {
        transitionTo(PaymentStatus.PROCESSING, now);
        record(new PaymentProcessingStarted(id, now));
    }

    public void markSettled(Instant now) {
        transitionTo(PaymentStatus.SETTLED, now);
        record(new PaymentSettled(id, amount, now));
    }

    public void markFailed(String reason, Instant now) {
        String checkedReason = requireReason(reason);
        transitionTo(PaymentStatus.FAILED, now);
        this.failureReason = checkedReason;
        record(new PaymentFailed(id, checkedReason, now));
    }

    public boolean isInitiatedBy(String subject) {
        return initiatedBy.equals(subject);
    }

    /** Returns and clears the events recorded since the aggregate was loaded or created. */
    public List<PaymentEvent> pullEvents() {
        List<PaymentEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    private void transitionTo(PaymentStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("PAYMENT_INVALID_TRANSITION",
                    "Payment %s cannot move from %s to %s".formatted(id, status, target));
        }
        this.status = target;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    private void record(PaymentEvent event) {
        pendingEvents.add(event);
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainRuleViolationException("PAYMENT_REASON_REQUIRED", "A reason is required");
        }
        return reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) : reason;
    }

    private static String normaliseReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String stripped = reference.strip();
        if (stripped.length() > MAX_REFERENCE_LENGTH) {
            throw new DomainRuleViolationException("PAYMENT_REFERENCE_TOO_LONG",
                    "Reference must be at most " + MAX_REFERENCE_LENGTH + " characters");
        }
        return stripped;
    }

    public PaymentId id() {
        return id;
    }

    public AccountId payerAccountId() {
        return payerAccountId;
    }

    public AccountId payeeAccountId() {
        return payeeAccountId;
    }

    public Money amount() {
        return amount;
    }

    public PaymentMethod method() {
        return method;
    }

    public String reference() {
        return reference;
    }

    public String initiatedBy() {
        return initiatedBy;
    }

    public PaymentStatus status() {
        return status;
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}
