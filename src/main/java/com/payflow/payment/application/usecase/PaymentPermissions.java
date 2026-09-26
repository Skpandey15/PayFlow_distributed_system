package com.payflow.payment.application.usecase;

/**
 * Permission names (OAuth2 scopes) understood by the Payment context. They are checked at the HTTP edge
 * and again here, so a future non-HTTP entry point (Kafka consumer, batch job) cannot bypass them.
 */
public final class PaymentPermissions {

    /** Read own payments. */
    public static final String READ = "payments:read";
    /** Create and cancel own payments. */
    public static final String WRITE = "payments:write";
    /** Drive payments through authorization and settlement (service/operator identity). */
    public static final String PROCESS = "payments:process";
    /** See every payment regardless of initiator (support/operations). */
    public static final String ADMIN = "payments:admin";

    private PaymentPermissions() {
    }
}
