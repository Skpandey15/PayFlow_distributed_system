package com.payflow.payment.domain.saga;

/**
 * Checkout evidence forwarded to fraud assessment. Kept on the saga so a recovery re-issue of the risk
 * command carries the same evidence as the original.
 */
public record CheckoutContext(String deviceId, String ipAddress, String userAgent, String countryCode) {

    public static final CheckoutContext NONE = new CheckoutContext(null, null, null, null);
}
