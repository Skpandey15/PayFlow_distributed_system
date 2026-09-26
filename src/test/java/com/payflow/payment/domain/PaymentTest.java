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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");
    static final Instant T1 = T0.plusSeconds(1);
    final AccountId payer = AccountId.newId();
    final AccountId payee = AccountId.newId();

    Payment newPayment() {
        return Payment.initiate(PaymentId.newId(), payer, payee, Money.of("125.50", "USD"), PaymentMethod.CARD,
                "  invoice 42  ", "alice", T0);
    }

    @Nested
    class Creation {

        @Test
        void startsCreatedAndRecordsPaymentCreated() {
            Payment p = newPayment();
            assertThat(p.status()).isEqualTo(PaymentStatus.CREATED);
            assertThat(p.reference()).isEqualTo("invoice 42");
            assertThat(p.version()).isZero();
            assertThat(p.pullEvents()).singleElement().isInstanceOf(PaymentCreated.class);
            assertThat(p.pullEvents()).as("events are drained once pulled").isEmpty();
        }

        @Test
        void rejectsPayingYourself() {
            assertThatThrownBy(() -> Payment.initiate(PaymentId.newId(), payer, payer, Money.of("1", "USD"),
                    PaymentMethod.CARD, null, "alice", T0))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .extracting("code").isEqualTo("PAYMENT_SAME_ACCOUNT");
        }

        @Test
        void rejectsZeroAndNegativeAmounts() {
            assertThatThrownBy(() -> Payment.initiate(PaymentId.newId(), payer, payee, Money.of("0", "USD"),
                    PaymentMethod.CARD, null, "alice", T0))
                    .extracting("code").isEqualTo("PAYMENT_AMOUNT_NOT_POSITIVE");
            assertThatThrownBy(() -> Payment.initiate(PaymentId.newId(), payer, payee, Money.of("-5", "USD"),
                    PaymentMethod.CARD, null, "alice", T0))
                    .extracting("code").isEqualTo("PAYMENT_AMOUNT_NOT_POSITIVE");
        }

        @Test
        void rejectsOverlongReference() {
            assertThatThrownBy(() -> Payment.initiate(PaymentId.newId(), payer, payee, Money.of("1", "USD"),
                    PaymentMethod.CARD, "x".repeat(141), "alice", T0))
                    .extracting("code").isEqualTo("PAYMENT_REFERENCE_TOO_LONG");
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void happyPathEmitsOneEventPerTransition() {
            Payment p = newPayment();
            p.pullEvents();
            p.authorize(T1);
            p.startProcessing(T1);
            p.markSettled(T1);
            assertThat(p.status()).isEqualTo(PaymentStatus.SETTLED);
            assertThat(p.updatedAt()).isEqualTo(T1);
            assertThat(p.pullEvents()).extracting(Object::getClass).containsExactly(
                    PaymentAuthorized.class, PaymentProcessingStarted.class, PaymentSettled.class);
        }

        @Test
        void rejectionCarriesReason() {
            Payment p = newPayment();
            p.reject("RISK_DECLINED:HIGH_AMOUNT", T1);
            assertThat(p.status()).isEqualTo(PaymentStatus.REJECTED);
            assertThat(p.failureReason()).isEqualTo("RISK_DECLINED:HIGH_AMOUNT");
            assertThat(p.pullEvents()).last().isInstanceOf(PaymentRejected.class);
        }

        @Test
        void failureRequiresAReason() {
            Payment p = newPayment();
            p.authorize(T1);
            p.startProcessing(T1);
            assertThatThrownBy(() -> p.markFailed(" ", T1)).extracting("code").isEqualTo("PAYMENT_REASON_REQUIRED");
            p.markFailed("SETTLEMENT_DECLINED", T1);
            assertThat(p.pullEvents()).last().isInstanceOf(PaymentFailed.class);
        }

        @Test
        void canBeCancelledBeforeProcessingButNotAfter() {
            Payment created = newPayment();
            created.cancel(T1);
            assertThat(created.pullEvents()).last().isInstanceOf(PaymentCancelled.class);

            Payment authorized = newPayment();
            authorized.authorize(T1);
            authorized.cancel(T1);
            assertThat(authorized.status()).isEqualTo(PaymentStatus.CANCELLED);

            Payment processing = newPayment();
            processing.authorize(T1);
            processing.startProcessing(T1);
            assertThatThrownBy(() -> processing.cancel(T1))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("PROCESSING to CANCELLED");
        }

        @Test
        void aFailedTransitionLeavesStateAndEventsUntouched() {
            Payment p = newPayment();
            p.pullEvents();
            assertThatThrownBy(() -> p.markSettled(T1)).isInstanceOf(InvalidStateTransitionException.class);
            assertThat(p.status()).isEqualTo(PaymentStatus.CREATED);
            assertThat(p.updatedAt()).isEqualTo(T0);
            assertThat(p.pullEvents()).isEmpty();
        }
    }

    @Nested
    class StateMachine {

        @ParameterizedTest
        @EnumSource(value = PaymentStatus.class, names = {"REJECTED", "CANCELLED", "SETTLED", "FAILED"})
        void terminalStatesAllowNoTransitions(PaymentStatus terminal) {
            assertThat(terminal.isTerminal()).isTrue();
            for (PaymentStatus target : PaymentStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).as("%s -> %s", terminal, target).isFalse();
            }
        }

        @Test
        void allowedTransitionsAreExactlyTheDocumentedOnes() {
            assertThat(allowedFrom(PaymentStatus.CREATED))
                    .containsExactlyInAnyOrder(PaymentStatus.AUTHORIZED, PaymentStatus.REJECTED, PaymentStatus.CANCELLED);
            assertThat(allowedFrom(PaymentStatus.AUTHORIZED))
                    .containsExactlyInAnyOrder(PaymentStatus.PROCESSING, PaymentStatus.CANCELLED);
            assertThat(allowedFrom(PaymentStatus.PROCESSING))
                    .containsExactlyInAnyOrder(PaymentStatus.SETTLED, PaymentStatus.FAILED);
        }

        private Set<PaymentStatus> allowedFrom(PaymentStatus from) {
            Set<PaymentStatus> allowed = EnumSet.noneOf(PaymentStatus.class);
            for (PaymentStatus to : PaymentStatus.values()) {
                if (from.canTransitionTo(to)) {
                    allowed.add(to);
                }
            }
            return allowed;
        }
    }

    @Test
    void snapshotRoundTripPreservesStateWithoutReplayingEvents() {
        Payment p = newPayment();
        p.authorize(T1);
        Payment rehydrated = Payment.rehydrate(p.snapshot());
        assertThat(rehydrated.snapshot()).isEqualTo(p.snapshot());
        assertThat(rehydrated.pullEvents()).isEmpty();
    }
}
