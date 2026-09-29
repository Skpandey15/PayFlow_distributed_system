package com.payflow.payment.application.port.in;

import com.payflow.payment.domain.PaymentId;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * WP-03 (K3): operating the MANUAL_REVIEW state. Every operation requires {@code ops:manual-review}; every decision
 * is idempotent (Idempotency-Key), audited (append-only) and traceable (the saga's correlation id).
 *
 * <p>Decisions deliberately cannot set a financial outcome. They either resume the workflow (participants are
 * idempotent per payment, so they re-establish the true outcome) or, with the rail's evidence, block the instruction
 * at the rail and resume, so the normal decline path compensates. There is no "force success".
 */
public interface ManualReviewUseCase {

    PageResult<ReviewCase> list(Actor actor, PageQuery page);

    CaseDetail get(Actor actor, PaymentId paymentId);

    DecisionResult decide(Actor actor, DecideCommand command);

    enum Decision {
        /** Re-issue the timed-out step's command. Safe while the rail still deduplicates the payment's key. */
        RESUME,
        /** Only from AWAITING_SETTLEMENT: void the instruction at the rail (refused if the rail accepted it), then resume. */
        CONFIRM_NOT_SETTLED
    }

    record ReviewCase(UUID paymentId, UUID sagaId, String escalatedFrom, String reason, Instant inReviewSince,
                      BigDecimal amount, String currency, String method, String correlationId) {
    }

    record SettlementFacts(String rail, String localStatus, int submissionAttempts, String lastAttemptOutcome,
                           String lastErrorCode, Instant lastAttemptAt, String railState, String railDetail) {
    }

    record DecisionView(UUID id, String decision, String operator, String reason, String ticketReference,
                        Instant decidedAt) {
    }

    record CaseDetail(ReviewCase reviewCase, String paymentStatus, SettlementFacts settlement,
                      List<Decision> allowedDecisions, List<DecisionView> history) {
    }

    record DecideCommand(PaymentId paymentId, Decision decision, String reason, String ticketReference,
                         String idempotencyKey) {
    }

    record DecisionResult(UUID decisionId, UUID paymentId, Decision decision, String resumedStep, boolean replayed) {
    }
}
