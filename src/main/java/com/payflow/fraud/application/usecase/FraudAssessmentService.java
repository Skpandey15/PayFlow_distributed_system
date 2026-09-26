package com.payflow.fraud.application.usecase;

import com.payflow.fraud.application.port.in.AssessPaymentRiskUseCase;
import com.payflow.fraud.application.port.in.GetFraudAssessmentUseCase;
import com.payflow.fraud.application.port.out.FraudAssessmentRepositoryPort;
import com.payflow.fraud.application.port.out.FraudAssessmentRepositoryPort.DuplicateAssessmentException;
import com.payflow.fraud.application.port.out.RiskDecisionPublisherPort;
import com.payflow.fraud.domain.ChannelContext;
import com.payflow.fraud.domain.FraudAssessment;
import com.payflow.fraud.domain.RiskDecision;
import com.payflow.fraud.domain.RiskEvaluationContext;
import com.payflow.fraud.domain.RiskScoringPolicy;
import com.payflow.fraud.domain.RiskSignal;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.NotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.payflow.shared.application.ForbiddenException.requirePermission;

/**
 * Fraud use cases. No {@code TransactionRunner} here: each assessment is a single MongoDB document,
 * and single-document writes are atomic, so multi-document transactions (and a replica-set requirement)
 * are unnecessary. Uniqueness of one assessment per payment is enforced by a unique index.
 */
public class FraudAssessmentService implements AssessPaymentRiskUseCase, GetFraudAssessmentUseCase {

    public static final String PERMISSION_READ = "fraud:read";

    private final FraudAssessmentRepositoryPort assessments;
    private final RiskDecisionPublisherPort decisions;
    private final RiskScoringPolicy policy;
    private final Duration velocityWindow;
    private final Clock clock;

    public FraudAssessmentService(FraudAssessmentRepositoryPort assessments, RiskDecisionPublisherPort decisions,
                                  RiskScoringPolicy policy, Duration velocityWindow, Clock clock) {
        this.assessments = assessments;
        this.decisions = decisions;
        this.policy = policy;
        this.velocityWindow = velocityWindow;
        this.clock = clock;
    }

    /**
     * Assess once, announce every time: a repeated command (redelivery, saga recovery) gets the stored decision
     * re-published instead of a fresh score, so the decision never flips. Order matters: persist the decision
     * <em>before</em> publishing it. A crash in between redelivers the command, which then publishes the stored decision.
     */
    @Override
    public RiskAssessmentView assess(AssessRiskCommand command) {
        FraudAssessment assessment = assessOnce(command);
        decisions.publish(assessment);
        return view(assessment);
    }

    private FraudAssessment assessOnce(AssessRiskCommand command) {
        var existing = assessments.findByPaymentId(command.paymentId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant now = clock.instant();
        long recent = assessments.countByPayerSince(command.payerAccountId(), now.minus(velocityWindow));
        RiskEvaluationContext context = new RiskEvaluationContext(command.paymentId(), command.payerAccountId(),
                command.payeeAccountId(), command.amount(), command.paymentMethod(), toDomain(command.channel()), recent);
        FraudAssessment assessment = policy.assess(context, now);
        try {
            assessments.save(assessment);
            return assessment;
        } catch (DuplicateAssessmentException raced) {
            // A concurrent delivery assessed the same payment first; its decision stands.
            return assessments.findByPaymentId(command.paymentId()).orElseThrow();
        }
    }

    @Override
    public FraudAssessment getByPaymentId(Actor actor, UUID paymentId) {
        requirePermission(actor, PERMISSION_READ);
        return assessments.findByPaymentId(paymentId)
                .orElseThrow(() -> new NotFoundException("FRAUD_ASSESSMENT_NOT_FOUND",
                        "No assessment for payment " + paymentId));
    }

    private static ChannelContext toDomain(Channel channel) {
        return channel == null ? ChannelContext.EMPTY
                : new ChannelContext(channel.deviceId(), channel.ipAddress(), channel.userAgent(), channel.countryCode());
    }

    private static RiskAssessmentView view(FraudAssessment a) {
        boolean approved = a.decision() == RiskDecision.APPROVE;
        return new RiskAssessmentView(a.id(), a.paymentId(), approved, a.riskScore(),
                approved ? null : a.declineReason(), a.signals().stream().map(RiskSignal::code).toList(),
                a.modelVersion());
    }
}
