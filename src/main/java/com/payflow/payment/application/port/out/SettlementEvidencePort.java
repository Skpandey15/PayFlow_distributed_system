package com.payflow.payment.application.port.out;

import com.payflow.payment.domain.PaymentId;

import java.time.Instant;
import java.util.Optional;

/** What the settlement side knows about a payment, in Payment's own terms (used only by manual review). */
public interface SettlementEvidencePort {

    Optional<Evidence> evidence(PaymentId paymentId);

    /** Blocks the payment's instruction at the rail; returns what the rail then holds. */
    RailState voidAtRail(PaymentId paymentId);

    enum RailState { ACCEPTED, DECLINED, NOT_FOUND, UNREACHABLE }

    record Evidence(String rail, String localStatus, int submissionAttempts, String lastAttemptOutcome,
                    String lastErrorCode, Instant lastAttemptAt, RailState railState, String railDetail) {
    }
}
