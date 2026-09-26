package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.CreatePaymentUseCase;
import com.payflow.payment.application.port.in.PaymentView;
import com.payflow.payment.application.port.out.AccountLookupPort;
import com.payflow.payment.application.port.out.AccountLookupPort.PaymentParty;
import com.payflow.payment.application.port.out.IdempotencyStorePort;
import com.payflow.payment.application.port.out.IdempotencyStorePort.IdempotencyKeyConflictException;
import com.payflow.payment.application.port.out.IdempotencyStorePort.IdempotencyRecord;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.shared.application.NotFoundException;
import com.payflow.shared.application.TransactionRunner;
import com.payflow.shared.application.UnprocessableException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

/**
 * Creates a payment exactly once per (client, Idempotency-Key).
 *
 * <pre>
 * 1. read idempotency record (fast path)   -> replay if present (payload fingerprint must match)
 * 2. validate parties via Account context  -> outside the write transaction (cross-context read)
 * 3. BEGIN
 *      INSERT payment
 *      INSERT idempotency_record            -- PK(client_id, idempotency_key)
 *      publish PaymentCreated (in-tx port; WP-02 outbox)
 *    COMMIT
 * 4. on unique violation in step 3         -> a concurrent duplicate won; its row is committed; replay it
 * </pre>
 *
 * Why the database constraint and not "check then insert": two concurrent requests can both pass step 1
 * before either commits. Only the unique index serialises them. PostgreSQL makes the second INSERT wait
 * for the first transaction and then fail, so exactly one payment is ever created (see
 * {@code IdempotencyConcurrencyIT}).
 */
public class CreatePaymentService implements CreatePaymentUseCase {

    private final PaymentRepositoryPort payments;
    private final IdempotencyStorePort idempotency;
    private final AccountLookupPort accounts;
    private final PaymentEventPublisherPort events;
    private final TransactionRunner tx;
    private final Clock clock;
    private final Duration idempotencyRetention;

    public CreatePaymentService(PaymentRepositoryPort payments, IdempotencyStorePort idempotency,
                                AccountLookupPort accounts, PaymentEventPublisherPort events, TransactionRunner tx,
                                Clock clock, Duration idempotencyRetention) {
        this.payments = payments;
        this.idempotency = idempotency;
        this.accounts = accounts;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
        this.idempotencyRetention = idempotencyRetention;
    }

    @Override
    public CreatePaymentResult create(CreatePaymentCommand command) {
        requirePermission(command.actor(), PaymentPermissions.WRITE);
        String clientId = command.actor().subject();
        String fingerprint = RequestFingerprint.of(command);

        Optional<IdempotencyRecord> previous = tx.readOnly(() -> idempotency.find(clientId, command.idempotencyKey()));
        if (previous.isPresent()) {
            return replay(previous.get(), fingerprint);
        }

        validateParties(command);

        try {
            return tx.inTransaction(() -> {
                Instant now = clock.instant();
                Payment payment = Payment.initiate(PaymentId.newId(), command.payerAccountId(),
                        command.payeeAccountId(), command.amount(), command.method(), command.reference(),
                        clientId, now);
                payments.add(payment);
                idempotency.add(new IdempotencyRecord(clientId, command.idempotencyKey(), fingerprint,
                        payment.id(), now, now.plus(idempotencyRetention)));
                events.publish(payment.pullEvents());
                return new CreatePaymentResult(PaymentView.from(payment), false);
            });
        } catch (IdempotencyKeyConflictException concurrentDuplicate) {
            IdempotencyRecord winner = tx.readOnly(() -> idempotency.find(clientId, command.idempotencyKey()))
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency conflict reported but no committed record found", concurrentDuplicate));
            return replay(winner, fingerprint);
        }
    }

    private CreatePaymentResult replay(IdempotencyRecord record, String fingerprint) {
        if (!record.requestFingerprint().equals(fingerprint)) {
            throw new UnprocessableException("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used for a different request payload");
        }
        Payment original = tx.readOnly(() -> PaymentAccess.load(payments, record.paymentId()));
        return new CreatePaymentResult(PaymentView.from(original), true);
    }

    private void validateParties(CreatePaymentCommand command) {
        String caller = command.actor().subject();
        // Payer must exist AND belong to the caller: you can only pay from your own account.
        // A foreign account is reported as "not found" so account ids cannot be probed.
        PaymentParty payer = accounts.find(command.payerAccountId())
                .filter(p -> p.isOwnedBy(caller))
                .orElseThrow(() -> new NotFoundException("PAYER_ACCOUNT_NOT_FOUND",
                        "Payer account " + command.payerAccountId() + " not found"));
        PaymentParty payee = accounts.find(command.payeeAccountId())
                .orElseThrow(() -> new UnprocessableException("PAYEE_ACCOUNT_NOT_FOUND",
                        "Payee account " + command.payeeAccountId() + " does not exist"));
        if (!payer.canTransact()) {
            throw new UnprocessableException("PAYER_ACCOUNT_INACTIVE", "Payer account cannot send payments");
        }
        if (!payee.canTransact()) {
            throw new UnprocessableException("PAYEE_ACCOUNT_INACTIVE", "Payee account cannot receive payments");
        }
        if (!command.amount().hasCurrency(payer.currency()) || !command.amount().hasCurrency(payee.currency())) {
            throw new UnprocessableException("CURRENCY_MISMATCH",
                    "Payment currency must match both accounts' currency (no FX in WP-01)");
        }
    }
}
