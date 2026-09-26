package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.AuthorizePaymentUseCase.AuthorizePaymentCommand;
import com.payflow.payment.application.port.in.AuthorizePaymentUseCase.CheckoutChannel;
import com.payflow.payment.application.port.in.PaymentView;
import com.payflow.payment.application.port.out.AccountLookupPort;
import com.payflow.payment.application.port.out.AccountLookupPort.PaymentParty;
import com.payflow.payment.application.port.out.FraudAssessmentPort;
import com.payflow.payment.application.port.out.FraudAssessmentPort.FraudCheckUnavailableException;
import com.payflow.payment.application.port.out.FraudAssessmentPort.RiskVerdict;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.InvalidStateTransitionException;
import com.payflow.shared.domain.Money;
import com.payflow.support.Actors;
import com.payflow.support.DirectTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizePaymentServiceTest {

    static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
    final Map<AccountId, PaymentParty> directory = new HashMap<>();
    final AccountLookupPort accounts = id -> Optional.ofNullable(directory.get(id));
    final FraudAssessmentPort fraud = mock(FraudAssessmentPort.class);
    final DirectTransactionRunner tx = new DirectTransactionRunner();
    final AuthorizePaymentService service = new AuthorizePaymentService(payments, accounts, fraud,
            mock(PaymentEventPublisherPort.class), tx, Clock.fixed(NOW, ZoneOffset.UTC));

    final AccountId payer = AccountId.newId();
    final AccountId payee = AccountId.newId();
    PaymentId paymentId;

    @BeforeEach
    void setUp() {
        directory.put(payer, new PaymentParty(payer, "alice", Money.currency("USD"), true));
        directory.put(payee, new PaymentParty(payee, "bob", Money.currency("USD"), true));
        Payment payment = Payment.initiate(PaymentId.newId(), payer, payee, Money.of("10", "USD"), PaymentMethod.CARD,
                null, "alice", NOW);
        payments.add(payment);
        paymentId = payment.id();
    }

    PaymentView authorize() {
        return service.authorize(new AuthorizePaymentCommand(Actors.processor(), paymentId,
                new CheckoutChannel("device-1", "203.0.113.7", "ua", "US")));
    }

    @Test
    void approvedRiskAuthorizesAndTheRiskCallHappensOutsideAnyTransaction() {
        when(fraud.assess(any())).thenAnswer(invocation -> {
            assertThat(tx.isActive()).as("no DB transaction may be open during the remote risk call").isFalse();
            return new RiskVerdict(true, null);
        });

        assertThat(authorize().status()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    void declinedRiskRejectsWithReason() {
        when(fraud.assess(any())).thenReturn(new RiskVerdict(false, "RISK_DECLINED:HIGH_AMOUNT"));

        PaymentView view = authorize();

        assertThat(view.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(view.failureReason()).isEqualTo("RISK_DECLINED:HIGH_AMOUNT");
    }

    @Test
    void fraudOutageFailsClosedAndLeavesThePaymentRetryable() {
        when(fraud.assess(any())).thenThrow(new FraudCheckUnavailableException(new RuntimeException("mongo down")));

        assertThatThrownBy(this::authorize).isInstanceOf(FraudCheckUnavailableException.class);
        assertThat(payments.statusOf(paymentId)).isEqualTo(PaymentStatus.CREATED);
        assertThat(tx.readWriteTransactions()).as("nothing was written").isZero();
    }

    @Test
    void frozenPayerIsRejectedWithoutCallingFraud() {
        directory.put(payer, new PaymentParty(payer, "alice", Money.currency("USD"), false));

        assertThat(authorize().failureReason()).isEqualTo("PAYER_ACCOUNT_INELIGIBLE");
        verify(fraud, never()).assess(any());
    }

    @Test
    void alreadyDecidedPaymentIsReturnedUnchanged() {
        when(fraud.assess(any())).thenReturn(new RiskVerdict(true, null));
        authorize();

        PaymentView again = authorize();

        assertThat(again.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        verify(fraud, org.mockito.Mockito.times(1)).assess(any());
    }

    @Test
    void cancelledPaymentCannotBeAuthorized() {
        Payment p = payments.findById(paymentId).orElseThrow();
        p.cancel(NOW);
        payments.update(p);

        assertThatThrownBy(this::authorize).isInstanceOf(InvalidStateTransitionException.class);
        verify(fraud, never()).assess(any());
    }
}
