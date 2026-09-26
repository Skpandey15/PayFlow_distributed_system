package com.payflow.fraud.domain.rules;

import com.payflow.fraud.domain.RiskEvaluationContext;
import com.payflow.fraud.domain.RiskRule;
import com.payflow.fraud.domain.RiskSignal;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;

/**
 * Flags amounts at or above a per-currency threshold. Thresholds are per currency because 10,000 JPY and
 * 10,000 USD are very different exposures; currencies without a configured threshold are not scored.
 */
public final class HighAmountRule implements RiskRule {

    public static final String CODE = "HIGH_AMOUNT";

    private final Map<Currency, BigDecimal> thresholds;
    private final int score;

    public HighAmountRule(Map<Currency, BigDecimal> thresholds, int score) {
        this.thresholds = Map.copyOf(thresholds);
        this.score = score;
    }

    @Override
    public Optional<RiskSignal> evaluate(RiskEvaluationContext context) {
        BigDecimal threshold = thresholds.get(context.amount().currency());
        if (threshold == null || context.amount().amount().compareTo(threshold) < 0) {
            return Optional.empty();
        }
        return Optional.of(new RiskSignal(CODE, score,
                "Amount %s is at or above threshold %s".formatted(context.amount(), threshold.toPlainString())));
    }
}
