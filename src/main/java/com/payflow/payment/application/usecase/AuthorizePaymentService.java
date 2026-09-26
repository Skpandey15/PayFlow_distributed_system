package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.AuthorizePaymentUseCase;
import com.payflow.payment.application.port.in.PaymentView;
import com.payflow.payment.application.port.out.AccountLookupPort;
import com.payflow.payment.application.port.out.AccountLookupPort.PaymentParty;
import com.payflow.payment.application.port.out.FraudAssessmentPort;
import com.payflow.payment.application.port.out.FraudAssessmentPort.RiskCheck;
import com.payflow.payment.application.port.out.FraudAssessmentPort.RiskVerdict;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.application.TransactionRunner;

import java.time.Clock;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

/**
 * Authorization = eligibility (both accounts can still transact) + risk decision.
 *
 * <pre>
 *   read payment (short read-only tx)
 *   eligibility check via Account context   -- outside any tx
 *   risk assessment via Fraud context       -- outside any tx (MongoDB write, may be slow/unavailable)
 *   BEGIN; reload; CREATED -> AUTHORIZED | REJECTED; optimistic-lock update; publish; COMMIT
 * </pre>
 *
 * No PostgreSQL transaction or row lock is held while the fraud check runs, so a slow risk engine cannot
 * exhaust the connection pool or block other writers. If Fraud is down the payment stays CREATED
 * (fail closed) and the caller gets a retryable 503. Because the assessment is idempotent per payment,
 * the retry gets the same decision.
 */
public class AuthorizePaymentService implements AuthorizePaymentUseCase {

    private final PaymentRepositoryPort payments;
    private final AccountLookupPort accounts;
    private final FraudAssessmentPort fraud;
    private final PaymentEventPublisherPort events;
    private final TransactionRunner tx;
    private final Clock clock;

    public AuthorizePaymentService(PaymentRepositoryPort payments, AccountLookupPort accounts,
                                   FraudAssessmentPort fraud, PaymentEventPublisherPort events,
                                   TransactionRunner tx, Clock clock) {
        this.payments = payments;
        this.accounts = accounts;
        this.fraud = fraud;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public PaymentView authorize(AuthorizePaymentCommand command) {
        requirePermission(command.actor(), PaymentPermissions.PROCESS);
        Payment snapshot = tx.readOnly(() -> PaymentAccess.load(payments, command.paymentId()));
        if (snapshot.status() != PaymentStatus.CREATED && snapshot.status() != PaymentStatus.CANCELLED) {
            return PaymentView.from(snapshot); // already decided: idempotent retry
        }

        String ineligibility = snapshot.status() == PaymentStatus.CREATED ? ineligibilityReason(snapshot) : null;
        RiskVerdict verdict = (snapshot.status() == PaymentStatus.CREATED && ineligibility == null)
                ? fraud.assess(riskCheck(snapshot, command.channel()))
                : null;

        return tx.inTransaction(() -> {
            Payment payment = PaymentAccess.load(payments, command.paymentId());
            if (payment.status() == PaymentStatus.AUTHORIZED || payment.status() == PaymentStatus.REJECTED) {
                return PaymentView.from(payment); // a concurrent authorizer decided first
            }
            if (ineligibility != null) {
                payment.reject(ineligibility, clock.instant());
            } else if (verdict != null && verdict.approved()) {
                payment.authorize(clock.instant());
            } else if (verdict != null) {
                payment.reject(verdict.reason(), clock.instant());
            } else {
                payment.authorize(clock.instant()); // CANCELLED: the domain rejects the transition (409)
            }
            payments.update(payment);
            events.publish(payment.pullEvents());
            return PaymentView.from(payment);
        });
    }

    private String ineligibilityReason(Payment payment) {
        boolean payerOk = accounts.find(payment.payerAccountId()).map(PaymentParty::canTransact).orElse(false);
        if (!payerOk) {
            return "PAYER_ACCOUNT_INELIGIBLE";
        }
        boolean payeeOk = accounts.find(payment.payeeAccountId()).map(PaymentParty::canTransact).orElse(false);
        return payeeOk ? null : "PAYEE_ACCOUNT_INELIGIBLE";
    }

    private static RiskCheck riskCheck(Payment p, CheckoutChannel channel) {
        CheckoutChannel c = channel == null ? new CheckoutChannel(null, null, null, null) : channel;
        return new RiskCheck(p.id().value(), p.payerAccountId(), p.payeeAccountId(), p.amount(), p.method(),
                c.deviceId(), c.ipAddress(), c.userAgent(), c.countryCode());
    }
}
