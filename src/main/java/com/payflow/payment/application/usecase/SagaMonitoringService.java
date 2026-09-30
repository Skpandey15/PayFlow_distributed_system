package com.payflow.payment.application.usecase;

import com.payflow.payment.application.port.in.SagaMonitoringUseCase;
import com.payflow.payment.application.port.out.PaymentSagaRepositoryPort;
import com.payflow.shared.application.TransactionRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class SagaMonitoringService implements SagaMonitoringUseCase {

    private final PaymentSagaRepositoryPort sagas;
    private final TransactionRunner tx;
    private final Clock clock;

    public SagaMonitoringService(PaymentSagaRepositoryPort sagas, TransactionRunner tx, Clock clock) {
        this.sagas = sagas;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public long inPipeline() {
        return tx.readOnly(sagas::countInPipeline);
    }

    @Override
    public List<OpenStep> openSagas() {
        Instant now = clock.instant();
        return tx.readOnly(sagas::countOpenByStep).stream()
                .map(c -> new OpenStep(c.step(), c.count(), Duration.between(c.oldestStepStartedAt(), now)))
                .toList();
    }
}
