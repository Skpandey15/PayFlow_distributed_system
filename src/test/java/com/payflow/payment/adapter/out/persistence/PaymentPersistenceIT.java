package com.payflow.payment.adapter.out.persistence;

import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.application.ConcurrencyConflictException;
import com.payflow.shared.application.TransactionRunner;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import com.payflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class PaymentPersistenceIT {

    @Autowired
    PaymentRepositoryPort payments;
    @Autowired
    TransactionRunner tx;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;

    Payment newPayment(String amount, String currency) {
        return Payment.initiate(PaymentId.newId(), AccountId.newId(), AccountId.newId(), Money.of(amount, currency),
                PaymentMethod.UPI, "ref", "persistence-it", clock.instant());
    }

    @Test
    void aggregateSurvivesTheRoundTripUnchanged() {
        Payment original = newPayment("10.5", "USD");
        tx.inTransaction(() -> {
            payments.add(original);
            return null;
        });

        Payment loaded = tx.readOnly(() -> payments.findById(original.id())).orElseThrow();

        // Money scale, currency, timestamps (microsecond precision) and version all survive NUMERIC(19,4)/TIMESTAMPTZ.
        assertThat(loaded.snapshot()).isEqualTo(original.snapshot());
        assertThat(loaded.amount().amount().toPlainString()).isEqualTo("10.50");
        assertThat(jdbc.queryForObject("select amount::text from payment.payment where id = ?", String.class,
                original.id().value())).isEqualTo("10.5000");
    }

    @Test
    void staleWriteIsRejectedInsteadOfSilentlyOverwritingALaterState() {
        Payment p = newPayment("20", "EUR");
        tx.inTransaction(() -> {
            payments.add(p);
            return null;
        });
        // Two actors read the same version...
        Payment readByCanceller = tx.readOnly(() -> payments.findById(p.id())).orElseThrow();
        Payment readByAuthorizer = tx.readOnly(() -> payments.findById(p.id())).orElseThrow();

        // ...the canceller commits first...
        readByCanceller.cancel(clock.instant());
        tx.inTransaction(() -> {
            payments.update(readByCanceller);
            return null;
        });

        // ...so the authorizer's decision, based on a state that no longer exists, must not win (lost update).
        readByAuthorizer.authorize(clock.instant());
        assertThatThrownBy(() -> tx.inTransaction(() -> {
            payments.update(readByAuthorizer);
            return null;
        })).isInstanceOf(ConcurrencyConflictException.class);

        Payment finalState = tx.readOnly(() -> payments.findById(p.id())).orElseThrow();
        assertThat(finalState.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(finalState.version()).isEqualTo(1L);
    }

    @Test
    void databaseConstraintsHoldEvenWhenTheDomainIsBypassed() {
        UUID account = UUID.randomUUID();
        String insert = """
                insert into payment.payment (id, payer_account_id, payee_account_id, amount, currency, method, status,
                    initiated_by, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'sql', 0, now(), now())""";

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), account, UUID.randomUUID(), -1, "USD", "CARD", "CREATED"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_payment_amount_positive");
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), account, account, 1, "USD", "CARD", "CREATED"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_payment_distinct_parties");
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), account, UUID.randomUUID(), 1, "USD", "CARD", "TELEPORTED"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_payment_status");
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), account, UUID.randomUUID(), 1, "usd", "CARD", "CREATED"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_payment_currency_iso");
    }
}
