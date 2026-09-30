package com.payflow.payment.application.port.out;

import com.payflow.payment.domain.PaymentId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Append-only audit of operator decisions (no update, no delete: enforced by database grants too). */
public interface ManualReviewAuditPort {

    void append(DecisionRecord record);

    Optional<DecisionRecord> findByIdempotencyKey(String idempotencyKey);

    List<DecisionRecord> findByPayment(PaymentId paymentId);

    record DecisionRecord(UUID id, String idempotencyKey, PaymentId paymentId, UUID sagaId, String decision,
                          String escalatedFrom, String reason, String ticketReference, String operatorSubject,
                          String evidenceJson, String correlationId, Instant decidedAt) {
    }
}
