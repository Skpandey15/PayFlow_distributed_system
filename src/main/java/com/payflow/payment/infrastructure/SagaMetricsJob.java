package com.payflow.payment.infrastructure;

import com.payflow.payment.application.port.in.SagaMonitoringUseCase;
import com.payflow.payment.application.port.in.SagaMonitoringUseCase.OpenStep;
import com.payflow.payment.domain.saga.SagaStep;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes {@code payflow.saga.open{step}} and {@code payflow.saga.oldest.age.seconds{step}}. These answer "are
 * payments getting stuck, and where?" and feed the saga-stuck and manual-review alerts. Every replica publishes
 * the same database-wide value, so dashboards aggregate with {@code max}, not {@code sum}.
 */
@Component
class SagaMetricsJob {

    private static final Logger log = LoggerFactory.getLogger(SagaMetricsJob.class);
    private static final List<SagaStep> OPEN_STEPS = Arrays.stream(SagaStep.values()).filter(s -> !s.isTerminal()).toList();

    private final SagaMonitoringUseCase monitoring;
    private final MultiGauge open;
    private final MultiGauge oldestAge;

    SagaMetricsJob(SagaMonitoringUseCase monitoring, MeterRegistry registry) {
        this.monitoring = monitoring;
        this.open = MultiGauge.builder("payflow.saga.open").description("Open (non-terminal) sagas per step")
                .register(registry);
        this.oldestAge = MultiGauge.builder("payflow.saga.oldest.age.seconds")
                .description("Age of the oldest saga in each open step").baseUnit("seconds").register(registry);
    }

    @Scheduled(fixedDelayString = "${payflow.saga.metrics-interval-ms:15000}", initialDelay = 5000)
    void refresh() {
        try {
            Map<SagaStep, OpenStep> byStep = new EnumMap<>(SagaStep.class);
            monitoring.openSagas().forEach(s -> byStep.put(s.step(), s));
            // Every open step is always published (0 when empty) so alerts do not go silent on absent series.
            open.register(OPEN_STEPS.stream().map(step -> MultiGauge.Row.of(Tags.of("step", step.name()),
                    byStep.containsKey(step) ? byStep.get(step).count() : 0)).toList(), true);
            oldestAge.register(OPEN_STEPS.stream().map(step -> MultiGauge.Row.of(Tags.of("step", step.name()),
                    byStep.containsKey(step) ? byStep.get(step).oldestAge().toSeconds() : 0)).toList(), true);
        } catch (RuntimeException e) {
            log.atDebug().addKeyValue("errorCode", e.getClass().getSimpleName()).log("saga metrics refresh failed");
        }
    }
}
