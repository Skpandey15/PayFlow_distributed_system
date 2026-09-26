package com.payflow.contracts.payment;

import static com.payflow.contracts.ContractViolation.required;

/**
 * Public integration events of the Payment context (topic {@code payment.events}, key = paymentId).
 * These are the published language, deliberately separate from the internal domain events and from
 * JPA entities. Amounts are decimal strings, never binary floating point.
 */
public final class PaymentEvents {

    private PaymentEvents() {
    }

    public record PaymentCreatedV1(String paymentId, String payerAccountId, String payeeAccountId, String amount,
                                   String currency, String method) {
        public PaymentCreatedV1 {
            required(paymentId, "paymentId");
            required(payerAccountId, "payerAccountId");
            required(payeeAccountId, "payeeAccountId");
            required(amount, "amount");
            required(currency, "currency");
            required(method, "method");
        }
    }

    public record PaymentAuthorizedV1(String paymentId) {
        public PaymentAuthorizedV1 {
            required(paymentId, "paymentId");
        }
    }

    public record PaymentProcessingStartedV1(String paymentId) {
        public PaymentProcessingStartedV1 {
            required(paymentId, "paymentId");
        }
    }

    public record PaymentRejectedV1(String paymentId, String reason) {
        public PaymentRejectedV1 {
            required(paymentId, "paymentId");
            required(reason, "reason");
        }
    }

    public record PaymentCancelledV1(String paymentId) {
        public PaymentCancelledV1 {
            required(paymentId, "paymentId");
        }
    }

    public record PaymentSettledV1(String paymentId, String amount, String currency) {
        public PaymentSettledV1 {
            required(paymentId, "paymentId");
            required(amount, "amount");
            required(currency, "currency");
        }
    }

    public record PaymentFailedV1(String paymentId, String reason) {
        public PaymentFailedV1 {
            required(paymentId, "paymentId");
            required(reason, "reason");
        }
    }
}
