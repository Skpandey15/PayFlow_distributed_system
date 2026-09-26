package com.payflow.contracts.funds;

import static com.payflow.contracts.ContractViolation.required;

/** Funds (Account context) contracts: {@code funds.commands} and {@code funds.events}. */
public final class FundsMessages {

    private FundsMessages() {
    }

    // ---------------------------------------------------------------- commands (from the payment saga)

    public record ReserveFundsV1(String paymentId, String payerAccountId, String payeeAccountId, String amount,
                                 String currency) {
        public ReserveFundsV1 {
            required(paymentId, "paymentId");
            required(payerAccountId, "payerAccountId");
            required(payeeAccountId, "payeeAccountId");
            required(amount, "amount");
            required(currency, "currency");
        }
    }

    public record CaptureFundsV1(String paymentId) {
        public CaptureFundsV1 {
            required(paymentId, "paymentId");
        }
    }

    /** Compensation. Carries payer and amount so a release that overtakes its reservation can leave a tombstone. */
    public record ReleaseFundsV1(String paymentId, String payerAccountId, String amount, String currency, String reason) {
        public ReleaseFundsV1 {
            required(paymentId, "paymentId");
            required(payerAccountId, "payerAccountId");
            required(amount, "amount");
            required(currency, "currency");
            required(reason, "reason");
        }
    }

    // ---------------------------------------------------------------- events (from the Account context)

    public record FundsReservedV1(String paymentId, String reservationId, String payerAccountId, String amount,
                                  String currency) {
        public FundsReservedV1 {
            required(paymentId, "paymentId");
            required(reservationId, "reservationId");
            required(payerAccountId, "payerAccountId");
            required(amount, "amount");
            required(currency, "currency");
        }
    }

    public record FundsReservationFailedV1(String paymentId, String reason) {
        public FundsReservationFailedV1 {
            required(paymentId, "paymentId");
            required(reason, "reason");
        }
    }

    public record FundsCapturedV1(String paymentId, String payerAccountId, String payeeAccountId, String amount,
                                  String currency) {
        public FundsCapturedV1 {
            required(paymentId, "paymentId");
            required(payerAccountId, "payerAccountId");
            required(payeeAccountId, "payeeAccountId");
            required(amount, "amount");
            required(currency, "currency");
        }
    }

    public record FundsReleasedV1(String paymentId, String amount, String currency, String reason) {
        public FundsReleasedV1 {
            required(paymentId, "paymentId");
            required(amount, "amount");
            required(currency, "currency");
            required(reason, "reason");
        }
    }

    /** Key = accountId (not a payment-workflow message). */
    public record FundsDepositedV1(String depositId, String accountId, String amount, String currency) {
        public FundsDepositedV1 {
            required(depositId, "depositId");
            required(accountId, "accountId");
            required(amount, "amount");
            required(currency, "currency");
        }
    }
}
