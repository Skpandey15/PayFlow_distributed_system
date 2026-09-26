package com.payflow.payment.adapter.in.web;

import com.payflow.payment.domain.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create-payment request. {@code amount} is a <b>decimal string</b>, not a JSON number. JSON numbers are
 * parsed as binary floating point by many clients (JavaScript), so "0.1 + 0.2" style errors would corrupt
 * amounts before they reach us. Syntax is validated here; currency-specific precision is enforced by the
 * domain {@code Money}.
 */
public record CreatePaymentRequest(
        @NotNull UUID payerAccountId,
        @NotNull UUID payeeAccountId,
        @NotBlank
        @Pattern(regexp = "^\\d{1,15}(\\.\\d{1,4})?$",
                message = "must be a positive decimal string with at most 15 integer and 4 fraction digits")
        @Schema(example = "125.50", description = "Decimal string in major units")
        String amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO-4217 alphabetic code")
        @Schema(example = "USD")
        String currency,
        @NotNull PaymentMethod method,
        @Size(max = 140) String reference) {
}
