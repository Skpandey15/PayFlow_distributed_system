package com.payflow.fraud.application.port.in;

import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;

import java.util.List;
import java.util.UUID;

/**
 * Published command API of the Fraud context. Idempotent per payment: assessing the same payment again
 * returns the original decision instead of re-scoring, because a decision must not flip on retry.
 *
 * <p>Throws {@link com.payflow.shared.application.DependencyUnavailableException} when the fraud store is
 * unavailable. Callers must treat that as "no decision" (fail closed), never as approval.
 */
public interface AssessPaymentRiskUseCase {

    RiskAssessmentView assess(AssessRiskCommand command);

    record AssessRiskCommand(UUID paymentId, AccountId payerAccountId, AccountId payeeAccountId, Money amount,
                             String paymentMethod, Channel channel) {
    }

    record Channel(String deviceId, String ipAddress, String userAgent, String countryCode) {
    }

    record RiskAssessmentView(UUID assessmentId, UUID paymentId, boolean approved, int riskScore,
                              String reason, List<String> signalCodes, String modelVersion) {
    }
}
