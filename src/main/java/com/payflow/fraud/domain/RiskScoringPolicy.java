package com.payflow.fraud.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Domain service that combines independent {@link RiskRule} strategies into a single scored decision.
 *
 * <p>Additive scoring capped at 100; at or above {@code declineThreshold} the payment is declined.
 * {@code modelVersion} is stamped on every assessment so decisions stay explainable after the rules
 * change, which matters for disputes and regulator questions.
 */
public final class RiskScoringPolicy {

    private final List<RiskRule> rules;
    private final int declineThreshold;
    private final String modelVersion;

    public RiskScoringPolicy(List<RiskRule> rules, int declineThreshold, String modelVersion) {
        if (declineThreshold < 1 || declineThreshold > 100) {
            throw new IllegalArgumentException("declineThreshold must be within 1..100");
        }
        this.rules = List.copyOf(rules);
        this.declineThreshold = declineThreshold;
        this.modelVersion = modelVersion;
    }

    public FraudAssessment assess(RiskEvaluationContext context, Instant now) {
        List<RiskSignal> signals = rules.stream()
                .map(rule -> rule.evaluate(context))
                .flatMap(Optional::stream)
                .toList();
        int score = Math.min(100, signals.stream().mapToInt(RiskSignal::score).sum());
        RiskDecision decision = score >= declineThreshold ? RiskDecision.DECLINE : RiskDecision.APPROVE;
        return FraudAssessment.record(context, score, decision, signals, modelVersion, now);
    }
}
