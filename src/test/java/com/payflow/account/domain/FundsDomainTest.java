package com.payflow.account.domain;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.DomainRuleViolationException;
import com.payflow.shared.domain.InvalidStateTransitionException;
import com.payflow.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundsDomainTest {

    final Instant now = Instant.parse("2026-09-01T10:00:00Z");
    final AccountId account = AccountId.newId();

    AccountBalance funded(String amount) {
        AccountBalance b = AccountBalance.open(account, Money.currency("USD"), now);
        b.credit(Money.of(amount, "USD"), now);
        return b;
    }

    @Test
    void reserveCaptureReleaseKeepAvailablePlusReservedConsistent() {
        AccountBalance b = funded("100.00");
        b.reserve(Money.of("30.00", "USD"), now);
        assertThat(b.available()).isEqualTo(Money.of("70.00", "USD"));
        assertThat(b.reserved()).isEqualTo(Money.of("30.00", "USD"));

        b.releaseReserved(Money.of("10.00", "USD"), now);
        assertThat(b.available()).isEqualTo(Money.of("80.00", "USD"));

        b.captureReserved(Money.of("20.00", "USD"), now);
        assertThat(b.reserved().isZero()).isTrue();
        assertThat(b.available()).isEqualTo(Money.of("80.00", "USD"));
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        AccountBalance b = funded("10.00");
        assertThat(b.canReserve(Money.of("10.01", "USD"))).isFalse();
        assertThatThrownBy(() -> b.reserve(Money.of("10.01", "USD"), now))
                .isInstanceOf(DomainRuleViolationException.class).extracting("code").isEqualTo("FUNDS_INSUFFICIENT");
        assertThat(b.available()).isEqualTo(Money.of("10.00", "USD"));
    }

    @Test
    void cannotCaptureOrReleaseMoreThanReserved() {
        AccountBalance b = funded("10.00");
        b.reserve(Money.of("5.00", "USD"), now);
        assertThatThrownBy(() -> b.captureReserved(Money.of("5.01", "USD"), now)).extracting("code")
                .isEqualTo("FUNDS_RESERVED_UNDERFLOW");
        assertThatThrownBy(() -> b.releaseReserved(Money.of("6.00", "USD"), now)).extracting("code")
                .isEqualTo("FUNDS_RESERVED_UNDERFLOW");
    }

    @Test
    void currencyMustMatchTheAccount() {
        assertThatThrownBy(() -> funded("10.00").reserve(Money.of("1.00", "EUR"), now)).extracting("code")
                .isEqualTo("FUNDS_CURRENCY_MISMATCH");
    }

    @Test
    void reservationLifecycle() {
        FundsReservation r = FundsReservation.reserved(UUID.randomUUID(), account, AccountId.newId(), Money.of("1", "USD"), now);
        r.capture(now);
        assertThat(r.status()).isEqualTo(ReservationStatus.CAPTURED);
        assertThatThrownBy(() -> r.release("late", now)).isInstanceOf(InvalidStateTransitionException.class);

        FundsReservation tombstone = FundsReservation.releasedWithoutReservation(UUID.randomUUID(), account,
                Money.of("1", "USD"), "CANCELLED", now);
        assertThat(tombstone.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThatThrownBy(() -> tombstone.capture(now)).isInstanceOf(InvalidStateTransitionException.class);
    }
}
