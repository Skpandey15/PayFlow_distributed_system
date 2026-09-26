package com.payflow.contracts.settlement;

import static com.payflow.contracts.ContractViolation.required;

/** Settlement contracts: {@code settlement.commands} and {@code settlement.events}. */
public final class SettlementMessages {

    private SettlementMessages() {
    }

    public record SubmitSettlementV1(String paymentId, String method, String amount, String currency, String reference) {
        public SubmitSettlementV1 {
            required(paymentId, "paymentId");
            required(method, "method");
            required(amount, "amount");
            required(currency, "currency");
        }
    }

    public record SettlementCompletedV1(String paymentId, String settlementId, String providerReference) {
        public SettlementCompletedV1 {
            required(paymentId, "paymentId");
            required(settlementId, "settlementId");
            required(providerReference, "providerReference");
        }
    }

    public record SettlementDeclinedV1(String paymentId, String settlementId, String reason) {
        public SettlementDeclinedV1 {
            required(paymentId, "paymentId");
            required(settlementId, "settlementId");
            required(reason, "reason");
        }
    }
}
