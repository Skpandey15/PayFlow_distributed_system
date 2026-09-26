package com.payflow.fraud.domain;

import com.payflow.fraud.domain.rules.HighAmountRule;
import com.payflow.fraud.domain.rules.HighRiskCountryRule;
import com.payflow.fraud.domain.rules.MissingDeviceRule;
import com.payflow.fraud.domain.rules.VelocityRule;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScoringPolicyTest {

    final RiskScoringPolicy policy = new RiskScoringPolicy(List.of(
            new HighAmountRule(Map.of(Money.currency("USD"), new BigDecimal("10000")), 50),
            new VelocityRule(10, 40),
            new MissingDeviceRule(15),
            new HighRiskCountryRule(Set.of("KP"), 70)), 70, "rules-test");

    RiskEvaluationContext context(String amount, String method, ChannelContext channel, long recentAttempts) {
        return new RiskEvaluationContext(UUID.randomUUID(), AccountId.newId(), AccountId.newId(),
                Money.of(amount, "USD"), method, channel, recentAttempts);
    }

    final ChannelContext withDevice = new ChannelContext("dev-1", "203.0.113.7", "ua", "US");

    @Test
    void lowRiskPaymentIsApprovedWithNoSignals() {
        FraudAssessment a = policy.assess(context("50.00", "CARD", withDevice, 0), Instant.now());
        assertThat(a.decision()).isEqualTo(RiskDecision.APPROVE);
        assertThat(a.riskScore()).isZero();
        assertThat(a.signals()).isEmpty();
        assertThat(a.modelVersion()).isEqualTo("rules-test");
    }

    @Test
    void signalsBelowThresholdStillApproveButAreRecordedAsEvidence() {
        FraudAssessment a = policy.assess(context("10000.00", "CARD", ChannelContext.EMPTY, 0), Instant.now());
        assertThat(a.riskScore()).isEqualTo(65);
        assertThat(a.decision()).isEqualTo(RiskDecision.APPROVE);
        assertThat(a.signals()).extracting(RiskSignal::code)
                .containsExactlyInAnyOrder(HighAmountRule.CODE, MissingDeviceRule.CODE);
    }

    @Test
    void combinedSignalsAtThresholdDecline() {
        FraudAssessment a = policy.assess(context("20000.00", "UPI", withDevice, 12), Instant.now());
        assertThat(a.riskScore()).isEqualTo(90);
        assertThat(a.decision()).isEqualTo(RiskDecision.DECLINE);
        assertThat(a.declineReason()).startsWith("RISK_DECLINED:").contains(HighAmountRule.CODE, VelocityRule.CODE);
    }

    @Test
    void highRiskCountryAloneDeclines() {
        FraudAssessment a = policy.assess(context("5.00", "CARD", new ChannelContext("d", null, null, "kp"), 0), Instant.now());
        assertThat(a.decision()).isEqualTo(RiskDecision.DECLINE);
    }

    @Test
    void scoreIsCappedAt100() {
        FraudAssessment a = policy.assess(context("99999.00", "CARD", new ChannelContext(null, null, null, "KP"), 50), Instant.now());
        assertThat(a.riskScore()).isEqualTo(100);
    }
}
