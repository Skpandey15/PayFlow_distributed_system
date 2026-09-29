package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.ManualReviewUseCase;
import com.payflow.payment.application.port.out.ManualReviewAuditPort;
import com.payflow.payment.application.port.out.ManualReviewAuditPort.DecisionRecord;
import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.application.port.out.PaymentSagaRepositoryPort;
import com.payflow.payment.application.port.out.SagaCommandPort;
import com.payflow.payment.application.port.out.SettlementEvidencePort;
import com.payflow.payment.application.port.out.SettlementEvidencePort.Evidence;
import com.payflow.payment.application.port.out.SettlementEvidencePort.RailState;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.saga.PaymentSaga;
import com.payflow.payment.domain.saga.SagaStep;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.ConflictException;
import com.payflow.shared.application.NotFoundException;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;
import com.payflow.shared.application.TransactionRunner;
import com.payflow.shared.application.UnprocessableException;
import com.payflow.shared.domain.Identifiers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

/**
 * Manual-review operations (WP-03, K3). Both decisions converge through the ordinary event flow:
 * <pre>
 *  RESUME               TX: saga MANUAL_REVIEW → escalated step, audit row, re-issue that step's command (outbox)
 *  CONFIRM_NOT_SETTLED  1. void the key at the rail (external, idempotent); refuse if the rail says ACCEPTED
 *                       2. TX: as RESUME. The re-issued SubmitSettlement meets the voided key, the rail declines
 *                          it, and the existing SettlementDeclined → compensation path releases the funds.
 * </pre>
 * Crash safety: if step 2 fails after step 1, the case stays in review and the operator repeats the decision (the
 * void is idempotent). If a crash follows step 2's commit, the command is in the outbox and will be delivered.
 */
public class ManualReviewService implements ManualReviewUseCase {

    private final PaymentRepositoryPort payments;
    private final PaymentSagaRepositoryPort sagas;
    private final SagaCommandPort commands;
    private final SettlementEvidencePort settlement;
    private final ManualReviewAuditPort audit;
    private final TransactionRunner tx;
    private final Clock clock;
    private final Duration railIdempotencyWindow;

    public ManualReviewService(PaymentRepositoryPort payments, PaymentSagaRepositoryPort sagas, SagaCommandPort commands,
                               SettlementEvidencePort settlement, ManualReviewAuditPort audit, TransactionRunner tx,
                               Clock clock, Duration railIdempotencyWindow) {
        this.payments = payments;
        this.sagas = sagas;
        this.commands = commands;
        this.settlement = settlement;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
        this.railIdempotencyWindow = railIdempotencyWindow;
    }

    @Override
    public PageResult<ReviewCase> list(Actor actor, PageQuery page) {
        requirePermission(actor, PaymentPermissions.MANUAL_REVIEW);
        return tx.readOnly(() -> sagas.findInManualReview(page)
                .map(saga -> toCase(saga, payments.findById(saga.paymentId()).orElseThrow())));
    }

    @Override
    public CaseDetail get(Actor actor, PaymentId paymentId) {
        requirePermission(actor, PaymentPermissions.MANUAL_REVIEW);
        var loaded = tx.readOnly(() -> {
            PaymentSaga saga = sagas.findByPaymentId(paymentId).orElseThrow(() -> notFound(paymentId));
            return new Object[]{saga, payments.findById(paymentId).orElseThrow(), audit.findByPayment(paymentId)};
        });
        PaymentSaga saga = (PaymentSaga) loaded[0];
        Payment payment = (Payment) loaded[1];
        @SuppressWarnings("unchecked")
        List<DecisionRecord> history = (List<DecisionRecord>) loaded[2];
        // Live rail inquiry happens outside any transaction (never hold a DB connection across a network call).
        Optional<Evidence> evidence = settlement.evidence(paymentId);
        return new CaseDetail(toCase(saga, payment), payment.status().name(), evidence.map(ManualReviewService::facts).orElse(null),
                allowed(saga, evidence),
                history.stream().map(d -> new DecisionView(d.id(), d.decision(), d.operatorSubject(), d.reason(),
                        d.ticketReference(), d.decidedAt())).toList());
    }

    @Override
    public DecisionResult decide(Actor actor, DecideCommand c) {
        requirePermission(actor, PaymentPermissions.MANUAL_REVIEW);
        if (c.reason() == null || c.reason().isBlank()) {
            throw new UnprocessableException("REVIEW_REASON_REQUIRED", "A reason is required for every review decision");
        }
        Optional<DecisionRecord> previous = audit.findByIdempotencyKey(c.idempotencyKey());
        if (previous.isPresent()) {
            DecisionRecord p = previous.get();
            if (!p.paymentId().equals(c.paymentId()) || !p.decision().equals(c.decision().name())) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was used for a different decision");
            }
            return new DecisionResult(p.id(), p.paymentId().value(), c.decision(), p.escalatedFrom(), true);
        }

        PaymentSaga current = tx.readOnly(() -> sagas.findByPaymentId(c.paymentId())).orElseThrow(() -> notFound(c.paymentId()));
        requireInReview(current);
        Optional<Evidence> evidence = settlement.evidence(c.paymentId());
        switch (c.decision()) {
            case RESUME -> {
                if (current.escalatedFrom() == SagaStep.AWAITING_SETTLEMENT
                        && current.createdAt().plus(railIdempotencyWindow).isBefore(clock.instant())) {
                    // Past the rail's deduplication window a re-submission could settle a second time.
                    throw new ConflictException("RAIL_IDEMPOTENCY_WINDOW_EXPIRED", "The settlement rail no longer "
                            + "deduplicates this payment; resubmitting could pay twice. Resolve with the rail provider.");
                }
            }
            case CONFIRM_NOT_SETTLED -> {
                if (current.escalatedFrom() != SagaStep.AWAITING_SETTLEMENT) {
                    throw new ConflictException("DECISION_NOT_APPLICABLE",
                            "CONFIRM_NOT_SETTLED applies only to payments whose settlement outcome is unknown");
                }
                RailState afterVoid = settlement.voidAtRail(c.paymentId());
                if (afterVoid == RailState.ACCEPTED) {
                    throw new ConflictException("RAIL_REPORTS_SETTLED",
                            "The rail settled this payment; it cannot be failed. RESUME to complete it.");
                }
            }
        }
        String evidenceJson = evidenceJson(evidence, c.decision());
        return tx.inTransaction(() -> {
            PaymentSaga saga = sagas.findByPaymentId(c.paymentId()).orElseThrow(() -> notFound(c.paymentId()));
            requireInReview(saga);
            Payment payment = payments.findById(c.paymentId()).orElseThrow();
            SagaStep escalatedFrom = saga.escalatedFrom();
            Instant now = clock.instant();
            SagaStep resumed = saga.resumeFromManualReview(now);
            sagas.update(saga); // optimistic lock: two operators cannot both resolve the same case
            DecisionRecord record = new DecisionRecord(Identifiers.timeOrderedUuid(), c.idempotencyKey(), c.paymentId(),
                    saga.sagaId(), c.decision().name(), escalatedFrom.name(), c.reason().strip(), c.ticketReference(),
                    actor.subject(), evidenceJson, saga.correlationId(), now);
            audit.append(record);
            reissue(resumed, payment, saga);
            return new DecisionResult(record.id(), c.paymentId().value(), c.decision(), resumed.name(), false);
        });
    }

    private void reissue(SagaStep step, Payment payment, PaymentSaga saga) {
        switch (step) {
            case AWAITING_SETTLEMENT -> commands.submitSettlement(payment, saga);
            case AWAITING_CAPTURE -> commands.captureFunds(payment, saga);
            case COMPENSATING -> commands.releaseFunds(payment, saga, saga.compensationReason().name());
            default -> throw new IllegalStateException("Unexpected resumed step " + step);
        }
    }

    private static List<Decision> allowed(PaymentSaga saga, Optional<Evidence> evidence) {
        if (saga.step() != SagaStep.MANUAL_REVIEW) {
            return List.of();
        }
        boolean railSettled = evidence.map(e -> e.railState() == RailState.ACCEPTED).orElse(false);
        return saga.escalatedFrom() == SagaStep.AWAITING_SETTLEMENT && !railSettled
                ? List.of(Decision.RESUME, Decision.CONFIRM_NOT_SETTLED)
                : List.of(Decision.RESUME);
    }

    private static void requireInReview(PaymentSaga saga) {
        if (saga.step() != SagaStep.MANUAL_REVIEW) {
            throw new ConflictException("SAGA_NOT_IN_MANUAL_REVIEW",
                    "Payment " + saga.paymentId() + " is not awaiting manual review (saga step " + saga.step() + ")");
        }
    }

    private static ReviewCase toCase(PaymentSaga saga, Payment payment) {
        return new ReviewCase(saga.paymentId().value(), saga.sagaId(),
                saga.escalatedFrom() == null ? null : saga.escalatedFrom().name(), saga.outcomeReason(),
                saga.stepStartedAt(), payment.amount().amount(), payment.amount().currencyCode(), payment.method().name(),
                saga.correlationId());
    }

    private static SettlementFacts facts(Evidence e) {
        return new SettlementFacts(e.rail(), e.localStatus(), e.submissionAttempts(), e.lastAttemptOutcome(),
                e.lastErrorCode(), e.lastAttemptAt(), e.railState().name(), e.railDetail());
    }

    /** Evidence the operator decided on, frozen into the audit row. Codes and states only; no personal data. */
    private static String evidenceJson(Optional<Evidence> evidence, Decision decision) {
        StringBuilder json = new StringBuilder("{\"decision\":\"").append(decision.name()).append('"');
        evidence.ifPresent(e -> json.append(",\"rail\":").append(q(e.rail()))
                .append(",\"settlementStatus\":").append(q(e.localStatus()))
                .append(",\"submissionAttempts\":").append(e.submissionAttempts())
                .append(",\"lastAttemptOutcome\":").append(q(e.lastAttemptOutcome()))
                .append(",\"lastErrorCode\":").append(q(e.lastErrorCode()))
                .append(",\"railState\":").append(q(e.railState().name())));
        return json.append('}').toString();
    }

    private static String q(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static NotFoundException notFound(PaymentId id) {
        return new NotFoundException("PAYMENT_SAGA_NOT_FOUND", "No saga for payment " + id);
    }
}
