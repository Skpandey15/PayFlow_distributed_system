package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.PaymentSagaUseCase.SagaTransition;
import com.payflow.payment.application.port.in.RecoverStuckSagasUseCase.RecoveryAction;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.application.port.out.SagaCommandPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.domain.saga.CheckoutContext;
import com.payflow.payment.domain.saga.PaymentSaga;
import com.payflow.payment.domain.saga.SagaStep;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import com.payflow.support.DirectTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PaymentSagaServiceTest {

    static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
    final InMemoryPaymentSagaRepository sagas = new InMemoryPaymentSagaRepository();
    final SagaCommandPort commands = mock(SagaCommandPort.class);
    final PaymentEventPublisherPort events = mock(PaymentEventPublisherPort.class);
    final DirectTransactionRunner tx = new DirectTransactionRunner();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final PaymentSagaService orchestrator = new PaymentSagaService(payments, sagas, commands, events, tx, clock);
    PaymentId id;

    @BeforeEach
    void setUp() {
        Payment p = Payment.initiate(PaymentId.newId(), AccountId.newId(), AccountId.newId(), Money.of("10", "USD"),
                PaymentMethod.CARD, null, "alice", NOW);
        payments.add(p);
        sagas.add(PaymentSaga.start(p.id(), "corr", CheckoutContext.NONE, NOW));
        id = p.id();
    }

    @Test
    void fullOrchestrationIssuesEachCommandOnceAndSettles() {
        orchestrator.onRiskAssessed(id, true, null);
        orchestrator.onFundsReserved(id);
        orchestrator.onSettlementCompleted(id);
        SagaTransition last = orchestrator.onFundsCaptured(id);

        assertThat(last.to()).isEqualTo(SagaStep.COMPLETED);
        assertThat(payments.statusOf(id)).isEqualTo(PaymentStatus.SETTLED);
        var order = inOrder(commands);
        order.verify(commands).reserveFunds(any(), any());
        order.verify(commands).submitSettlement(any(), any());
        order.verify(commands).captureFunds(any(), any());
        verify(commands, never()).releaseFunds(any(), any(), any());
    }

    @Test
    void duplicateReplyIsStaleAndIssuesNothing() {
        orchestrator.onRiskAssessed(id, true, null);
        SagaTransition again = orchestrator.onRiskAssessed(id, true, null);

        assertThat(again.applied()).isFalse();
        verify(commands, times(1)).reserveFunds(any(), any());
    }

    @Test
    void settlementDeclineFailsThePaymentAndCompensates() {
        orchestrator.onRiskAssessed(id, true, null);
        orchestrator.onFundsReserved(id);
        orchestrator.onSettlementDeclined(id, "SIMULATED_DECLINE");

        assertThat(payments.statusOf(id)).isEqualTo(PaymentStatus.FAILED);
        verify(commands).releaseFunds(any(), any(), eq("SETTLEMENT_DECLINED"));
        assertThat(orchestrator.onFundsReleased(id).to()).isEqualTo(SagaStep.FAILED);
    }

    @Test
    void stateIsPersistedBeforeCommandsAreIssued() {
        org.mockito.Mockito.doAnswer(inv -> {
            assertThat(sagas.get(id).step()).as("saga already persisted when the command is written")
                    .isEqualTo(SagaStep.AWAITING_FUNDS);
            return null;
        }).when(commands).reserveFunds(any(), any());

        orchestrator.onRiskAssessed(id, true, null);
    }

    @Test
    void recoveryReissuesThenCompensatesOnlyWhereSafe() {
        SagaPolicy policy = new SagaPolicy(Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofMinutes(2),
                Duration.ofSeconds(30), Duration.ofSeconds(30), 1, 10);
        orchestrator.onRiskAssessed(id, true, null); // AWAITING_FUNDS
        Clock later = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);
        SagaRecoveryService recovery = new SagaRecoveryService(payments, sagas, commands, events, tx, later, policy);

        assertThat(recovery.recoverOverdueSagas().actions()).singleElement()
                .extracting("action").isEqualTo(RecoveryAction.COMMAND_REISSUED);
        verify(commands, times(2)).reserveFunds(any(), any());

        SagaRecoveryService evenLater = new SagaRecoveryService(payments, sagas, commands, events, tx,
                Clock.fixed(NOW.plusSeconds(200), ZoneOffset.UTC), policy);
        assertThat(evenLater.recoverOverdueSagas().actions()).singleElement()
                .extracting("action").isEqualTo(RecoveryAction.COMPENSATION_STARTED);
        assertThat(payments.statusOf(id)).isEqualTo(PaymentStatus.REJECTED);
        verify(commands).releaseFunds(any(), any(), eq("TIMEOUT"));
        verify(events, org.mockito.Mockito.atLeastOnce()).publish(anyList());
    }

    /** R-2 follow-up: a parked settlement belongs to the Settlement context's resumer; recovery defers it. */
    @Test
    void recoveryDefersAParkedSettlementWithoutACommandOrARetry() {
        SagaPolicy policy = new SagaPolicy(Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofMinutes(2),
                Duration.ofSeconds(30), Duration.ofSeconds(30), 1, 10);
        orchestrator.onRiskAssessed(id, true, null);
        orchestrator.onFundsReserved(id); // AWAITING_SETTLEMENT
        int attemptsBefore = sagas.findByPaymentId(id).orElseThrow().stepAttempts();

        // Many step timeouts in a row (a long rail outage): never escalated, never re-issued.
        for (int minutes = 3; minutes <= 30; minutes += 3) {
            SagaRecoveryService parked = new SagaRecoveryService(payments, sagas, commands, events, tx,
                    Clock.fixed(NOW.plusSeconds(minutes * 60L), ZoneOffset.UTC), policy, () -> Duration.ZERO, p -> true);
            assertThat(parked.recoverOverdueSagas().actions()).singleElement()
                    .extracting("action").isEqualTo(RecoveryAction.SETTLEMENT_PARKED);
        }
        assertThat(sagas.findByPaymentId(id).orElseThrow().stepAttempts()).isEqualTo(attemptsBefore);
        assertThat(sagas.findByPaymentId(id).orElseThrow().step()).isEqualTo(SagaStep.AWAITING_SETTLEMENT);
        verify(commands, times(1)).submitSettlement(any(), any()); // only the original command

        // Not parked (or parking switched off): the normal re-issue path applies.
        SagaRecoveryService notParked = new SagaRecoveryService(payments, sagas, commands, events, tx,
                Clock.fixed(NOW.plusSeconds(35 * 60L), ZoneOffset.UTC), policy, () -> Duration.ZERO, p -> false);
        assertThat(notParked.recoverOverdueSagas().actions()).singleElement()
                .extracting("action").isEqualTo(RecoveryAction.COMMAND_REISSUED);
        verify(commands, times(2)).submitSettlement(any(), any());
    }

    /** WP-03: an unpublished command is not an unanswered command. Recovery holds instead of amplifying a backlog. */
    @Test
    void recoveryHoldsWhileCommandsAreNotBeingPublished() {
        SagaPolicy policy = new SagaPolicy(Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofMinutes(2),
                Duration.ofSeconds(30), Duration.ofSeconds(30), 1, 10, Duration.ofSeconds(15));
        orchestrator.onRiskAssessed(id, true, null); // AWAITING_FUNDS, and would be overdue at +60 s
        Clock later = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);

        SagaRecoveryService backlogged = new SagaRecoveryService(payments, sagas, commands, events, tx, later, policy,
                () -> Duration.ofSeconds(40));
        var report = backlogged.recoverOverdueSagas();
        assertThat(report.heldBecauseCommandsUnpublished()).isTrue();
        assertThat(report.actions()).isEmpty();
        verify(commands, times(1)).reserveFunds(any(), any()); // only the original command, no re-issue

        SagaRecoveryService caughtUp = new SagaRecoveryService(payments, sagas, commands, events, tx, later, policy,
                () -> Duration.ofSeconds(2));
        assertThat(caughtUp.recoverOverdueSagas().actions()).singleElement()
                .extracting("action").isEqualTo(RecoveryAction.COMMAND_REISSUED);
    }
}
