package com.payflow.payment.application.usecase;

import com.payflow.payment.domain.saga.SagaStep;

import java.time.Duration;
import java.util.stream.Stream;

/**
 * Step timeouts and retry budget of the payment saga. The settlement timeout is the longest because external
 * rails are the slowest participant, and an early timeout there only causes needless re-submissions.
 */
public record SagaPolicy(Duration riskTimeout, Duration fundsTimeout, Duration settlementTimeout,
                         Duration captureTimeout, Duration compensationTimeout, int maxStepAttempts, int recoveryBatchSize,
                         Duration recoveryHoldAge) {

    /** Without backpressure-aware holding (unit tests of the timeout rules themselves). */
    public SagaPolicy(Duration riskTimeout, Duration fundsTimeout, Duration settlementTimeout, Duration captureTimeout,
                      Duration compensationTimeout, int maxStepAttempts, int recoveryBatchSize) {
        this(riskTimeout, fundsTimeout, settlementTimeout, captureTimeout, compensationTimeout, maxStepAttempts,
                recoveryBatchSize, Duration.ofDays(36500));
    }

    public Duration timeoutFor(SagaStep step) {
        return switch (step) {
            case AWAITING_RISK -> riskTimeout;
            case AWAITING_FUNDS -> fundsTimeout;
            case AWAITING_SETTLEMENT -> settlementTimeout;
            case AWAITING_CAPTURE -> captureTimeout;
            case COMPENSATING -> compensationTimeout;
            default -> Duration.ofDays(36500);
        };
    }

    public Duration shortestTimeout() {
        return Stream.of(riskTimeout, fundsTimeout, settlementTimeout, captureTimeout, compensationTimeout)
                .min(Duration::compareTo).orElseThrow();
    }
}
