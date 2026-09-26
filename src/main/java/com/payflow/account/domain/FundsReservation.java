package com.payflow.account.domain;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Identifiers;
import com.payflow.shared.domain.InvalidStateTransitionException;
import com.payflow.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The hold placed on a payer's funds for one payment. At most one exists per payment (unique paymentId), so it
 * is the natural idempotency key for reserve, capture and release.
 *
 * <p>The <b>tombstone</b> ({@link #releasedWithoutReservation}) handles the out-of-order case where a
 * compensating ReleaseFunds is processed before its ReserveFunds (possible when the reserve command is
 * delayed in a retry topic). The late reserve then finds RELEASED and is refused, so money can never be held
 * for a payment that was already compensated.
 */
public final class FundsReservation {

    private final UUID id;
    private final UUID paymentId;
    private final AccountId payerAccountId;
    private final AccountId payeeAccountId;
    private final Money amount;
    private final Instant createdAt;
    private final long version;
    private ReservationStatus status;
    private String reason;
    private Instant updatedAt;

    private FundsReservation(UUID id, UUID paymentId, AccountId payer, AccountId payee, Money amount,
                             ReservationStatus status, String reason, long version, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.payerAccountId = Objects.requireNonNull(payer, "payer");
        this.payeeAccountId = payee;
        this.amount = Objects.requireNonNull(amount, "amount");
        this.status = Objects.requireNonNull(status, "status");
        this.reason = reason;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FundsReservation reserved(UUID paymentId, AccountId payer, AccountId payee, Money amount, Instant now) {
        return new FundsReservation(Identifiers.timeOrderedUuid(), paymentId, payer, payee, amount,
                ReservationStatus.RESERVED, null, 0L, now, now);
    }

    public static FundsReservation rejected(UUID paymentId, AccountId payer, AccountId payee, Money amount,
                                            String reason, Instant now) {
        return new FundsReservation(Identifiers.timeOrderedUuid(), paymentId, payer, payee, amount,
                ReservationStatus.REJECTED, reason, 0L, now, now);
    }

    public static FundsReservation releasedWithoutReservation(UUID paymentId, AccountId payer, Money amount,
                                                              String reason, Instant now) {
        return new FundsReservation(Identifiers.timeOrderedUuid(), paymentId, payer, null, amount,
                ReservationStatus.RELEASED, reason, 0L, now, now);
    }

    public static FundsReservation rehydrate(UUID id, UUID paymentId, AccountId payer, AccountId payee, Money amount,
                                             ReservationStatus status, String reason, long version, Instant createdAt,
                                             Instant updatedAt) {
        return new FundsReservation(id, paymentId, payer, payee, amount, status, reason, version, createdAt, updatedAt);
    }

    public void capture(Instant now) {
        require(ReservationStatus.RESERVED, "capture");
        status = ReservationStatus.CAPTURED;
        updatedAt = now;
    }

    public void release(String releaseReason, Instant now) {
        require(ReservationStatus.RESERVED, "release");
        status = ReservationStatus.RELEASED;
        reason = releaseReason;
        updatedAt = now;
    }

    private void require(ReservationStatus expected, String action) {
        if (status != expected) {
            throw new InvalidStateTransitionException("FUNDS_RESERVATION_" + status,
                    "Cannot " + action + " reservation for payment " + paymentId + " in status " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID paymentId() {
        return paymentId;
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

    public ReservationStatus status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
