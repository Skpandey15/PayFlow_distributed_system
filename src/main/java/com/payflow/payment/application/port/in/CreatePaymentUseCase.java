package com.payflow.payment.application.port.in;

import com.payflow.payment.domain.PaymentMethod;
import com.payflow.shared.application.Actor;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

import java.util.Objects;

public interface CreatePaymentUseCase {

    /**
     * Creates a payment exactly once per (caller, idempotency key).
     * A retry with the same key and the same payload replays the original result ({@code replayed=true}).
     * A retry with the same key and a different payload is rejected.
     */
    CreatePaymentResult create(CreatePaymentCommand command);

    record CreatePaymentCommand(Actor actor, String idempotencyKey, AccountId payerAccountId,
                                AccountId payeeAccountId, Money amount, PaymentMethod method, String reference) {
        public CreatePaymentCommand {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(payerAccountId, "payerAccountId");
            Objects.requireNonNull(payeeAccountId, "payeeAccountId");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(method, "method");
        }
    }

    record CreatePaymentResult(PaymentView payment, boolean replayed) {
    }
}
