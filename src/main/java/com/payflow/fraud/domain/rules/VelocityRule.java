package com.payflow.fraud.domain.rules;

import com.payflow.fraud.domain.RiskEvaluationContext;
import com.payflow.fraud.domain.RiskRule;
import com.payflow.fraud.domain.RiskSignal;

import java.util.Optional;

/** Flags payers who have attempted many payments within the velocity window (card-testing, account takeover). */
public final class VelocityRule implements RiskRule {

    public static final String CODE = "HIGH_VELOCITY";

    private final long maxAttemptsInWindow;
    private final int score;

    public VelocityRule(long maxAttemptsInWindow, int score) {
        this.maxAttemptsInWindow = maxAttemptsInWindow;
        this.score = score;
    }

    @Override
    public Optional<RiskSignal> evaluate(RiskEvaluationContext context) {
        if (context.payerAssessmentsInVelocityWindow() < maxAttemptsInWindow) {
            return Optional.empty();
        }
        return Optional.of(new RiskSignal(CODE, score,
                "%d prior payments in velocity window (limit %d)"
                        .formatted(context.payerAssessmentsInVelocityWindow(), maxAttemptsInWindow)));
    }
}
