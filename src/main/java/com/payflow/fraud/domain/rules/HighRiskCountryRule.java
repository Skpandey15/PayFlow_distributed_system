package com.payflow.fraud.domain.rules;

import com.payflow.fraud.domain.RiskEvaluationContext;
import com.payflow.fraud.domain.RiskRule;
import com.payflow.fraud.domain.RiskSignal;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Flags checkouts originating from configured high-risk jurisdictions (ISO-3166 alpha-2). */
public final class HighRiskCountryRule implements RiskRule {

    public static final String CODE = "HIGH_RISK_COUNTRY";

    private final Set<String> highRiskCountries;
    private final int score;

    public HighRiskCountryRule(Set<String> highRiskCountries, int score) {
        this.highRiskCountries = highRiskCountries.stream()
                .map(c -> c.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.score = score;
    }

    @Override
    public Optional<RiskSignal> evaluate(RiskEvaluationContext context) {
        String country = context.channel().countryCode();
        if (country == null || !highRiskCountries.contains(country.toUpperCase(Locale.ROOT))) {
            return Optional.empty();
        }
        return Optional.of(new RiskSignal(CODE, score, "Checkout country " + country + " is high risk"));
    }
}
