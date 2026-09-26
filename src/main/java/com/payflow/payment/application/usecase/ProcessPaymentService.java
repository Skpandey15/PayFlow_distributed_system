package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.PaymentView;
import com.payflow.payment.application.port.in.ProcessPaymentUseCase;
import com.payflow.payment.application.port.out.LedgerPostingPort;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.application.port.out.SettlementProviderPort;
import com.payflow.payment.application.port.out.SettlementProviderPort.SettlementOutcome;
import com.payflow.payment.application.port.out.SettlementProviderPort.SettlementRequest;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.TransactionRunner;
import com.payflow.shared.domain.InvalidStateTransitionException;

import java.time.Clock;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

/**
 * Orchestrates settlement of an authorized payment as a sequence of <b>short, resumable, idempotent steps</b>.
 * No database transaction spans a network or cross-context call.
 *
 * <pre>
 *   TX1  AUTHORIZED -> PROCESSING   (optimistic lock = "claim"; concurrent callers cannot both claim)
 *   ---  settlement.submit(...)      (idempotent per payment id; may throw "outcome unknown" -> stays PROCESSING)
 *   TX2  PROCESSING -> SETTLED | FAILED
 *   ---  ledger.recordSettlement     (idempotent per payment id)   &lt;-- DUAL WRITE, see below
 * </pre>
 *
 * <b>Known gap (WP-02):</b> if the process crashes after TX2 commits but before the ledger posting, the
 * payment is SETTLED without a journal entry until someone re-invokes {@code process} (which re-ensures
 * the posting). WP-02 closes this by writing {@code PaymentSettled} to a Transactional Outbox inside TX2
 * and letting the Ledger consume it.
 */
public class ProcessPaymentService implements ProcessPaymentUseCase {

    private final PaymentRepositoryPort payments;
    private final SettlementProviderPort settlement;
    private final LedgerPostingPort ledger;
    private final PaymentEventPublisherPort events;
    private final TransactionRunner tx;
    private final Clock clock;

    public ProcessPaymentService(PaymentRepositoryPort payments, SettlementProviderPort settlement,
                                 LedgerPostingPort ledger, PaymentEventPublisherPort events, TransactionRunner tx,
                                 Clock clock) {
        this.payments = payments;
        this.settlement = settlement;
        this.ledger = ledger;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public PaymentView process(Actor actor, PaymentId paymentId) {
        requirePermission(actor, PaymentPermissions.PROCESS);

        Payment claimed = tx.inTransaction(() -> {
            Payment payment = PaymentAccess.load(payments, paymentId);
            if (payment.status() == PaymentStatus.AUTHORIZED) {
                payment.startProcessing(clock.instant());
                payments.update(payment);
                events.publish(payment.pullEvents());
            }
            return payment;
        });

        switch (claimed.status()) {
            case PROCESSING -> { /* fresh claim or resumption of an interrupted run */ }
            case SETTLED -> {
                ensureLedgerPosted(claimed);
                return PaymentView.from(claimed);
            }
            case FAILED -> {
                return PaymentView.from(claimed);
            }
            default -> throw new InvalidStateTransitionException("PAYMENT_NOT_PROCESSABLE",
                    "Payment %s is %s; only AUTHORIZED payments can be processed".formatted(paymentId, claimed.status()));
        }

        SettlementOutcome outcome = settlement.submit(new SettlementRequest(claimed.id(), claimed.method(),
                claimed.amount(), claimed.reference()));

        Payment finished = tx.inTransaction(() -> {
            Payment payment = PaymentAccess.load(payments, paymentId);
            if (payment.status() == PaymentStatus.PROCESSING) {
                if (outcome.settled()) {
                    payment.markSettled(clock.instant());
                } else {
                    payment.markFailed(outcome.failureReason(), clock.instant());
                }
                payments.update(payment);
                events.publish(payment.pullEvents());
            }
            return payment;
        });

        if (finished.status() == PaymentStatus.SETTLED) {
            ensureLedgerPosted(finished);
        }
        return PaymentView.from(finished);
    }

    private void ensureLedgerPosted(Payment payment) {
        ledger.recordSettlement(payment.id(), payment.payerAccountId(), payment.payeeAccountId(), payment.amount());
    }
}
