package com.payflow.settlement.application.port.in;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Published operations API of the Settlement context, used to resolve payments whose settlement outcome is unknown.
 * It reports facts (our record and the rail's record) and can block an instruction at the rail. It never marks a
 * settlement completed or declined on an operator's say-so: outcomes come only from the rail.
 */
public interface SettlementOperationsUseCase {

    /** Our record plus a live inquiry at the rail. Empty if no settlement exists for the payment. */
    Optional<SettlementEvidence> evidence(UUID paymentId);

    /**
     * Voids the payment's instruction at the rail (idempotent), so nothing still in transit can settle later.
     * Returns what the rail then holds: VOIDED/DECLINED (safe to fail the payment) or ACCEPTED (the money moved).
     */
    RailRecord voidAtRail(UUID paymentId);

    enum RailStatus { ACCEPTED, DECLINED, NOT_FOUND, UNREACHABLE }

    record RailRecord(RailStatus status, String providerReference, String declineReason) {
    }

    record SettlementEvidence(UUID settlementId, String railName, String localStatus, int submissionAttempts,
                              String lastAttemptOutcome, String lastErrorCode, Instant lastAttemptAt,
                              RailRecord railRecord) {
    }
}
