package com.payflow.fraud.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** Externalised rule parameters: fraud teams tune thresholds without a code change. */
@ConfigurationProperties("payflow.fraud")
public record FraudProperties(
        @DefaultValue("70") int declineThreshold,
        @DefaultValue("rules-v1") String modelVersion,
        @DefaultValue("1h") Duration velocityWindow,
        @DefaultValue("10") long velocityMaxAttempts,
        @DefaultValue("40") int velocityScore,
        Map<String, BigDecimal> highAmountThresholds,
        @DefaultValue("50") int highAmountScore,
        @DefaultValue("15") int missingDeviceScore,
        Set<String> highRiskCountries,
        @DefaultValue("70") int highRiskCountryScore) {

    public FraudProperties {
        highAmountThresholds = highAmountThresholds == null ? Map.of() : Map.copyOf(highAmountThresholds);
        highRiskCountries = highRiskCountries == null ? Set.of() : Set.copyOf(highRiskCountries);
    }
}
