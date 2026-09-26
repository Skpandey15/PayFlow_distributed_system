package com.payflow.payment.adapter.in.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Optional checkout context forwarded to fraud assessment as evidence. */
public record AuthorizePaymentRequest(
        @Size(max = 128) String deviceId,
        @Size(max = 45) String ipAddress,
        @Size(max = 512) String userAgent,
        @Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO-3166 alpha-2 code") String countryCode) {
}
