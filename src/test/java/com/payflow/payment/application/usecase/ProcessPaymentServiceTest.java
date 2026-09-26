package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.out.LedgerPostingPort;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.application.port.out.SettlementProviderPort;
import com.payflow.payment.application.port.out.SettlementProviderPort.SettlementOutcome;
import com.payflow.payment.application.port.out.SettlementProviderPort.SettlementUnavailableException;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.InvalidStateTransitionException;
import com.payflow.shared.domain.Money;
import com.payflow.support.Actors;
import com.payflow.support.DirectTransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessPaymentServiceTest {

    static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
    final SettlementProviderPort settlement = mock(SettlementProviderPort.class);
    final LedgerPostingPort ledger = mock(LedgerPostingPort.class);
    final DirectTransactionRunner tx = new DirectTransactionRunner();
    final ProcessPaymentService service = new ProcessPaymentService(payments, settlement, ledger,
            mock(PaymentEventPublisherPort.class), tx, Clock.fixed(NOW, ZoneOffset.UTC));

    final AccountId payer = AccountId.newId();
    final AccountId payee = AccountId.newId();

    PaymentId paymentIn(PaymentStatus target) {
        Payment p = Payment.initiate(PaymentId.newId(), payer, payee, Money.of("40", "USD"), PaymentMethod.BANK_TRANSFER,
                "rent", "alice", NOW);
        if (target != PaymentStatus.CREATED) {
            p.authorize(NOW);
        }
        payments.add(p);
        return p.id();
    }

    @Test
    void settlesOutsideTransactionsAndPostsTheLedgerOnce() {
        PaymentId id = paymentIn(PaymentStatus.AUTHORIZED);
        when(settlement.submit(any())).thenAnswer(inv -> {
            assertThat(tx.isActive()).as("no DB transaction during the settlement call").isFalse();
            assertThat(payments.statusOf(id)).as("claimed before calling out").isEqualTo(PaymentStatus.PROCESSING);
            return new SettlementOutcome(true, "BANK-1", null);
        });

        assertThat(service.process(Actors.processor(), id).status()).isEqualTo(PaymentStatus.SETTLED);
        verify(ledger, times(1)).recordSettlement(id, payer, payee, Money.of("40", "USD"));
        assertThat(tx.readWriteTransactions()).as("claim + outcome").isEqualTo(2);
    }

    @Test
    void declinedSettlementFailsThePaymentAndPostsNothing() {
        PaymentId id = paymentIn(PaymentStatus.AUTHORIZED);
        when(settlement.submit(any())).thenReturn(new SettlementOutcome(false, null, "SETTLEMENT_DECLINED:SIMULATED_DECLINE"));

        var view = service.process(Actors.processor(), id);

        assertThat(view.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(view.failureReason()).isEqualTo("SETTLEMENT_DECLINED:SIMULATED_DECLINE");
        verify(ledger, never()).recordSettlement(any(), any(), any(), any());
    }

    @Test
    void unknownSettlementOutcomeLeavesPaymentProcessingAndARetryResumesIt() {
        PaymentId id = paymentIn(PaymentStatus.AUTHORIZED);
        when(settlement.submit(any()))
                .thenThrow(new SettlementUnavailableException(new RuntimeException("timeout")))
                .thenReturn(new SettlementOutcome(true, "BANK-1", null));

        assertThatThrownBy(() -> service.process(Actors.processor(), id)).isInstanceOf(SettlementUnavailableException.class);
        assertThat(payments.statusOf(id)).isEqualTo(PaymentStatus.PROCESSING);

        assertThat(service.process(Actors.processor(), id).status()).isEqualTo(PaymentStatus.SETTLED);
        verify(settlement, times(2)).submit(any());
    }

    @Test
    void reprocessingASettledPaymentOnlyReEnsuresTheLedgerPosting() {
        PaymentId id = paymentIn(PaymentStatus.AUTHORIZED);
        when(settlement.submit(any())).thenReturn(new SettlementOutcome(true, "BANK-1", null));
        service.process(Actors.processor(), id);

        service.process(Actors.processor(), id);

        verify(settlement, times(1)).submit(any());
        verify(ledger, times(2)).recordSettlement(any(), any(), any(), any()); // idempotent on the ledger side
    }

    @Test
    void unauthorizedPaymentCannotBeProcessed() {
        PaymentId id = paymentIn(PaymentStatus.CREATED);
        assertThatThrownBy(() -> service.process(Actors.processor(), id))
                .isInstanceOf(InvalidStateTransitionException.class)
                .extracting("code").isEqualTo("PAYMENT_NOT_PROCESSABLE");
        verify(settlement, never()).submit(any());
    }
}
