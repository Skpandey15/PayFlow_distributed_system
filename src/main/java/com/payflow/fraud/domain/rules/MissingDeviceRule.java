package com.payflow.fraud.domain.rules;

import com.payflow.fraud.domain.RiskEvaluationContext;
import com.payflow.fraud.domain.RiskRule;
import com.payflow.fraud.domain.RiskSignal;

import java.util.Optional;

/** Card-not-present payments without a device fingerprint are harder to attribute and are mildly riskier. */
public final class MissingDeviceRule implements RiskRule {

    public static final String CODE = "MISSING_DEVICE_FINGERPRINT";

    private final int score;

    public MissingDeviceRule(int score) {
        this.score = score;
    }

    @Override
    public Optional<RiskSignal> evaluate(RiskEvaluationContext context) {
        if (!"CARD".equals(context.paymentMethod()) || context.channel().hasDevice()) {
            return Optional.empty();
        }
        return Optional.of(new RiskSignal(CODE, score, "Card payment without device fingerprint"));
    }
}
