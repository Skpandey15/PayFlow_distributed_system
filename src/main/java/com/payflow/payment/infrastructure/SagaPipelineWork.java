package com.payflow.payment.infrastructure;

import com.payflow.payment.application.port.in.SagaMonitoringUseCase;
import com.payflow.platform.web.traffic.InFlightWork;
import org.springframework.stereotype.Component;

/** Admission control's view of the payment pipeline: sagas waiting on PayFlow's own processing. */
@Component
class SagaPipelineWork implements InFlightWork {

    private final SagaMonitoringUseCase monitoring;

    SagaPipelineWork(SagaMonitoringUseCase monitoring) {
        this.monitoring = monitoring;
    }

    @Override
    public long count() {
        return monitoring.inPipeline();
    }
}
