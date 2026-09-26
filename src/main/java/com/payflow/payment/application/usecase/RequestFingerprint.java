package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.CreatePaymentUseCase.CreatePaymentCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 over the canonical, business-relevant content of a create-payment request.
 *
 * <p>Canonicalisation uses parsed values, not raw JSON: the amount is already normalised to the
 * currency scale ("10.5" and "10.50" hash identically), and field order and whitespace are irrelevant.
 * Reusing a key with a different payload is therefore detected reliably, which is the classic
 * "same key, different amount" client bug.
 */
final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String of(CreatePaymentCommand c) {
        String canonical = String.join("|",
                "v1",
                c.payerAccountId().toString(),
                c.payeeAccountId().toString(),
                c.amount().amount().toPlainString(),
                c.amount().currencyCode(),
                c.method().name(),
                c.reference() == null ? "" : c.reference().strip());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
