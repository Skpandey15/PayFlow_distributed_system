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
        @DefaultValue("50") int recoveryBatchSize) {

    SagaPolicy toPolicy() {
        return new SagaPolicy(riskTimeout, fundsTimeout, settlementTimeout, captureTimeout, compensationTimeout,
                maxStepAttempts, recoveryBatchSize);
    }
}
