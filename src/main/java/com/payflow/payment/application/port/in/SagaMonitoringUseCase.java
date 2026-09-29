package com.payflow.payment.application.port.in;

import com.payflow.payment.domain.saga.SagaStep;

import java.time.Duration;
import java.util.List;

/**
 * Read model for operations: how many sagas are open in each non-terminal step, and how long the oldest one has
 * been there. Feeds the "saga stuck" and "manual review backlog" alerts; counts only, never payment ids.
 */
public interface SagaMonitoringUseCase {

    List<OpenStep> openSagas();

    record OpenStep(SagaStep step, long count, Duration oldestAge) {
    }
}
