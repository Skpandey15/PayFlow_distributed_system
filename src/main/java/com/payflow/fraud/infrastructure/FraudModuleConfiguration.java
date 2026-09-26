package com.payflow.fraud.infrastructure;

import com.payflow.fraud.application.port.out.FraudAssessmentRepositoryPort;
import com.payflow.fraud.application.port.out.RiskDecisionPublisherPort;
import com.payflow.fraud.application.usecase.FraudAssessmentService;
import com.payflow.fraud.domain.RiskRule;
import com.payflow.fraud.domain.RiskScoringPolicy;
import com.payflow.fraud.domain.rules.HighAmountRule;
import com.payflow.fraud.domain.rules.HighRiskCountryRule;
import com.payflow.fraud.domain.rules.MissingDeviceRule;
import com.payflow.fraud.domain.rules.VelocityRule;
import com.payflow.shared.domain.Money;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FraudProperties.class)
class FraudModuleConfiguration {

    @Bean
    RiskScoringPolicy riskScoringPolicy(FraudProperties p) {
        Map<Currency, BigDecimal> thresholds = p.highAmountThresholds().entrySet().stream()
                .collect(Collectors.toMap(e -> Money.currency(e.getKey()), Map.Entry::getValue));
        List<RiskRule> rules = List.of(
                new HighAmountRule(thresholds, p.highAmountScore()),
                new VelocityRule(p.velocityMaxAttempts(), p.velocityScore()),
                new MissingDeviceRule(p.missingDeviceScore()),
                new HighRiskCountryRule(p.highRiskCountries(), p.highRiskCountryScore()));
        return new RiskScoringPolicy(rules, p.declineThreshold(), p.modelVersion());
    }

    @Bean
    FraudAssessmentService fraudAssessmentService(FraudAssessmentRepositoryPort repository,
                                                  RiskDecisionPublisherPort decisions, RiskScoringPolicy policy,
                                                  FraudProperties p, Clock clock) {
        return new FraudAssessmentService(repository, decisions, policy, p.velocityWindow(), clock);
    }
}
