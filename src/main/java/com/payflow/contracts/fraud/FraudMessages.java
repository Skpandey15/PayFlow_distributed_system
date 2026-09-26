package com.payflow.contracts.fraud;

import java.util.List;

import static com.payflow.contracts.ContractViolation.required;

/** Fraud context contracts: {@code fraud.commands} (accepted by Fraud) and {@code fraud.events} (produced by Fraud). */
public final class FraudMessages {

    private FraudMessages() {
    }

    /** Command. Channel fields are optional evidence (personal data, so the topic ACL restricts readers to Fraud). */
    public record AssessPaymentRiskV1(String paymentId, String payerAccountId, String payeeAccountId, String amount,
                                      String currency, String method, String deviceId, String ipAddress,
                                      String userAgent, String countryCode) {
        public AssessPaymentRiskV1 {
            required(paymentId, "paymentId");
            required(payerAccountId, "payerAccountId");
            required(payeeAccountId, "payeeAccountId");
            required(amount, "amount");
            required(currency, "currency");
            required(method, "method");
        }
    }

    /** Original reply contract. Still accepted from the log (replay, old producers) through upcasting to V2. */
    public record RiskAssessedV1(String paymentId, boolean approved, int riskScore, String reason) {
        public RiskAssessedV1 {
            required(paymentId, "paymentId");
        }
    }

    /**
     * V2 is an <b>additive</b> change: {@code signalCodes} and {@code modelVersion} are new and optional-with-default,
     * which keeps it backward compatible. V1 consumers ignore the new fields (tolerant reader); V2 consumers read V1
     * through {@link #fromV1}.
     */
    public record RiskAssessedV2(String paymentId, boolean approved, int riskScore, String reason,
                                 List<String> signalCodes, String modelVersion) {
        public RiskAssessedV2 {
            required(paymentId, "paymentId");
            signalCodes = signalCodes == null ? List.of() : List.copyOf(signalCodes);
            modelVersion = modelVersion == null ? "unknown" : modelVersion;
        }

        public static RiskAssessedV2 fromV1(RiskAssessedV1 v1) {
            return new RiskAssessedV2(v1.paymentId(), v1.approved(), v1.riskScore(), v1.reason(), List.of(), "unknown");
        }
    }
}
