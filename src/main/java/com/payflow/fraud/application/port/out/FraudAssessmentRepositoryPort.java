package com.payflow.fraud.application.port.out;

import com.payflow.fraud.domain.FraudAssessment;
import com.payflow.shared.application.DependencyUnavailableException;
import com.payflow.shared.domain.AccountId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Document-store port for assessments. Implementations translate store outages into
 * {@link FraudStoreUnavailableException} so that callers can fail closed with a retryable error.
 */
public interface FraudAssessmentRepositoryPort {

    /**
     * @throws DuplicateAssessmentException if an assessment for the same payment was stored concurrently
     */
    void save(FraudAssessment assessment);

    Optional<FraudAssessment> findByPaymentId(UUID paymentId);

    long countByPayerSince(AccountId payerAccountId, Instant since);

    class DuplicateAssessmentException extends RuntimeException {
        public DuplicateAssessmentException(UUID paymentId, Throwable cause) {
            super("Assessment already exists for payment " + paymentId, cause);
        }
    }

    class FraudStoreUnavailableException extends DependencyUnavailableException {
        public FraudStoreUnavailableException(Throwable cause) {
            super("FRAUD_STORE_UNAVAILABLE", "Fraud assessment store is unavailable", cause);
        }
    }
}
