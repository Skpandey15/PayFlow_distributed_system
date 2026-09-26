package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import com.payflow.payment.application.port.in.CreatePaymentUseCase.CreatePaymentResult;
import com.payflow.payment.application.port.out.AccountLookupPort;
import com.payflow.payment.application.port.out.AccountLookupPort.PaymentParty;
import com.payflow.payment.application.port.out.IdempotencyStorePort.IdempotencyRecord;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.application.port.out.SagaCommandPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.domain.saga.CheckoutContext;
import com.payflow.payment.domain.saga.PaymentSaga;
import com.payflow.payment.domain.saga.SagaStep;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.ForbiddenException;
import com.payflow.shared.application.NotFoundException;
import com.payflow.shared.application.UnprocessableException;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import com.payflow.support.Actors;
import com.payflow.support.DirectTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CreatePaymentServiceTest {

    static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");
    static final Currency USD = Money.currency("USD");

    final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
    final InMemoryIdempotencyStore idempotency = new InMemoryIdempotencyStore();
    final Map<AccountId, PaymentParty> accountDirectory = new HashMap<>();
    final AccountLookupPort accounts = id -> Optional.ofNullable(accountDirectory.get(id));
    final PaymentEventPublisherPort events = mock(PaymentEventPublisherPort.class);
    final InMemoryPaymentSagaRepository sagas = new InMemoryPaymentSagaRepository();
    final SagaCommandPort commands = mock(SagaCommandPort.class);
    final DirectTransactionRunner tx = new DirectTransactionRunner();
    final CreatePaymentService service = new CreatePaymentService(payments, idempotency, accounts, sagas, commands,
            events, tx, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(24));

    final Actor alice = Actors.customer("alice");
    final AccountId aliceAccount = AccountId.newId();
    final AccountId bobAccount = AccountId.newId();

    @BeforeEach
    void accounts() {
        accountDirectory.put(aliceAccount, new PaymentParty(aliceAccount, "alice", USD, true));
        accountDirectory.put(bobAccount, new PaymentParty(bobAccount, "bob", USD, true));
    }

    CreatePaymentCommand command(Actor actor, String key, String amount) {
        return new CreatePaymentCommand(actor, key, aliceAccount, bobAccount, Money.of(amount, "USD"),
                PaymentMethod.CARD, "order-1", CheckoutContext.NONE, "corr-1");
    }

    @Test
    void createsPaymentAndIdempotencyRecordInOneTransaction() {
        CreatePaymentResult result = service.create(command(alice, "key-1", "25.00"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payments.rows).hasSize(1);
        IdempotencyRecord record = idempotency.find("alice", "key-1").orElseThrow();
        assertThat(record.paymentId().value()).isEqualTo(result.payment().id());
        assertThat(record.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(tx.readWriteTransactions()).as("exactly one write transaction").isEqualTo(1);
        verify(events, times(1)).publish(anyList());
        // The saga starts in the same transaction, and its first command is written with it (outbox).
        PaymentSaga saga = sagas.get(new PaymentId(result.payment().id()));
        assertThat(saga.step()).isEqualTo(SagaStep.AWAITING_RISK);
        assertThat(saga.correlationId()).isEqualTo("corr-1");
        verify(commands, times(1)).requestRiskAssessment(any(), any());
    }

    @Test
    void retryWithSameKeyAndPayloadReplaysTheOriginalPayment() {
        CreatePaymentResult first = service.create(command(alice, "key-1", "25.00"));
        CreatePaymentResult retry = service.create(command(alice, "key-1", "25.0")); // same amount, different scale

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.payment().id()).isEqualTo(first.payment().id());
        assertThat(payments.rows).as("no duplicate payment").hasSize(1);
        verify(events, times(1)).publish(anyList());
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        service.create(command(alice, "key-1", "25.00"));
        assertThatThrownBy(() -> service.create(command(alice, "key-1", "26.00")))
                .isInstanceOf(UnprocessableException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(payments.rows).hasSize(1);
    }

    @Test
    void losingAConcurrentRaceReplaysTheWinnerInsteadOfFailing() {
        // Another request with the same key passed the fast-path check at the same time and committed first.
        Payment winner = Payment.initiate(PaymentId.newId(), aliceAccount, bobAccount, Money.of("25.00", "USD"),
                PaymentMethod.CARD, "order-1", "alice", NOW);
        payments.add(winner);
        CreatePaymentCommand cmd = command(alice, "key-race", "25.00");
        idempotency.simulateConcurrentWinner(new IdempotencyRecord("alice", "key-race", RequestFingerprint.of(cmd),
                winner.id(), NOW, NOW.plusSeconds(60)));

        CreatePaymentResult result = service.create(cmd);

        assertThat(result.replayed()).isTrue();
        assertThat(result.payment().id()).isEqualTo(winner.id().value());
    }

    @Test
    void idempotencyKeysAreScopedPerClient() {
        accountDirectory.put(bobAccount, new PaymentParty(bobAccount, "bob", USD, true));
        CreatePaymentResult alicePayment = service.create(command(alice, "shared-key", "25.00"));
        CreatePaymentResult bobPayment = service.create(new CreatePaymentCommand(Actors.customer("bob"), "shared-key",
                bobAccount, aliceAccount, Money.of("25.00", "USD"), PaymentMethod.CARD, "order-1", CheckoutContext.NONE, "corr-1"));

        assertThat(bobPayment.replayed()).isFalse();
        assertThat(bobPayment.payment().id()).isNotEqualTo(alicePayment.payment().id());
    }

    @Test
    void callerMustOwnThePayerAccount() {
        assertThatThrownBy(() -> service.create(new CreatePaymentCommand(Actors.customer("mallory"), "k",
                aliceAccount, bobAccount, Money.of("1", "USD"), PaymentMethod.CARD, null, CheckoutContext.NONE, "corr-1")))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("PAYER_ACCOUNT_NOT_FOUND");
        assertThat(payments.rows).isEmpty();
    }

    @Test
    void currencyMustMatchBothAccounts() {
        assertThatThrownBy(() -> service.create(new CreatePaymentCommand(alice, "k", aliceAccount, bobAccount,
                Money.of("1", "EUR"), PaymentMethod.CARD, null, CheckoutContext.NONE, "corr-1")))
                .extracting("code").isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void inactivePayeeIsRejected() {
        accountDirectory.put(bobAccount, new PaymentParty(bobAccount, "bob", USD, false));
        assertThatThrownBy(() -> service.create(command(alice, "k", "1.00")))
                .extracting("code").isEqualTo("PAYEE_ACCOUNT_INACTIVE");
    }

    @Test
    void requiresWritePermission() {
        Actor readOnly = new Actor("alice", Set.of("payments:read"));
        assertThatThrownBy(() -> service.create(command(readOnly, "k", "1.00"))).isInstanceOf(ForbiddenException.class);
    }
}
