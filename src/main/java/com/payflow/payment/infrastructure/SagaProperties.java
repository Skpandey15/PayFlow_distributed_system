package com.payflow.payment.infrastructure;

import com.payflow.payment.application.usecase.SagaPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("payflow.saga")
public record SagaProperties(
        @DefaultValue("30s") Duration riskTimeout,
        @DefaultValue("30s") Duration fundsTimeout,
        @DefaultValue("2m") Duration settlementTimeout,
        @DefaultValue("30s") Duration captureTimeout,
        @DefaultValue("30s") Duration compensationTimeout,
        @DefaultValue("3") int maxStepAttempts,
        @DefaultValue("50") int recoveryBatchSize,
        @DefaultValue("15s") Duration recoveryHoldAge,
        // How long the settlement rails deduplicate an idempotency key (provider contract). Manual-review RESUME of an
        // unknown settlement is refused after this, because a re-submission could then settle twice.
        @DefaultValue("24h") Duration railIdempotencyWindow) {

    SagaPolicy toPolicy() {
        return new SagaPolicy(riskTimeout, fundsTimeout, settlementTimeout, captureTimeout, compensationTimeout,
                maxStepAttempts, recoveryBatchSize, recoveryHoldAge);
    }
}
