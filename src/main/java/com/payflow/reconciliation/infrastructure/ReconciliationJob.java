package com.payflow.reconciliation.infrastructure;

import com.payflow.reconciliation.application.port.in.ReconciliationUseCase;
import com.payflow.reconciliation.application.port.in.ReconciliationUseCase.OpenSummary;
import com.payflow.reconciliation.application.port.in.ReconciliationUseCase.RunReport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs reconciliation periodically and turns its results into metrics:
 * <ul>
 *   <li>{@code payflow.reconciliation.runs{result}} and {@code payflow.reconciliation.duration}</li>
 *   <li>{@code payflow.reconciliation.records.checked} (last run)</li>
 *   <li>{@code payflow.reconciliation.mismatches.open{check, severity, confirmed}}: the alerting signal</li>
 *   <li>{@code payflow.reconciliation.mismatch.oldest.age.seconds}</li>
 *   <li>{@code payflow.reconciliation.mismatch.amount{currency}}: absolute misstatement, per currency only</li>
 * </ul>
 * No payment or account ids ever become labels (cardinality and data exposure); they are in the mismatch records
 * behind the authenticated ops API. The job logs one line per run with findings (WARN) and nothing when clean.
 */
@Component
class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger("payflow.reconciliation");

    private final ReconciliationUseCase reconciliation;
    private final MeterRegistry meters;
    private final Clock clock;
    private final MultiGauge open;
    private final MultiGauge amount;
    private final AtomicLong recordsChecked = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();

    ReconciliationJob(ReconciliationUseCase reconciliation, MeterRegistry meters, Clock clock) {
        this.reconciliation = reconciliation;
        this.meters = meters;
        this.clock = clock;
        this.open = MultiGauge.builder("payflow.reconciliation.mismatches.open").register(meters);
        this.amount = MultiGauge.builder("payflow.reconciliation.mismatch.amount").register(meters);
        Gauge.builder("payflow.reconciliation.records.checked", recordsChecked, AtomicLong::get)
                .strongReference(true).register(meters);
        Gauge.builder("payflow.reconciliation.mismatch.oldest.age.seconds", oldestAgeSeconds, AtomicLong::get)
                .strongReference(true).register(meters);
    }

    @Scheduled(fixedDelayString = "${payflow.reconciliation.interval-ms:300000}", initialDelay = 60000)
    void runScheduled() {
        long started = System.nanoTime();
        try {
            RunReport report = reconciliation.run();
            if (report.skipped()) {
                Counter.builder("payflow.reconciliation.runs").tag("result", "SKIPPED").register(meters).increment();
            } else {
                Counter.builder("payflow.reconciliation.runs").tag("result", "OK").register(meters).increment();
                Timer.builder("payflow.reconciliation.duration").register(meters)
                        .record(Duration.ofNanos(System.nanoTime() - started));
                recordsChecked.set(report.recordsChecked());
                if (!report.findingsByCheck().isEmpty()) {
                    log.atWarn().addKeyValue("runId", report.runId()).addKeyValue("findings", report.findingsByCheck())
                            .addKeyValue("durationMs", report.duration().toMillis())
                            .log("reconciliation found mismatches");
                }
            }
        } catch (RuntimeException e) {
            Counter.builder("payflow.reconciliation.runs").tag("result", "FAILED").register(meters).increment();
            log.atWarn().addKeyValue("errorCode", e.getClass().getSimpleName()).log("reconciliation run failed");
        }
        refreshGauges();
    }

    void refreshGauges() {
        try {
            List<OpenSummary> summary = reconciliation.openSummary();
            open.register(summary.stream().map(s -> MultiGauge.Row.of(Tags.of("check", s.check(), "severity", s.severity(),
                    "confirmed", Boolean.toString(s.confirmed()), "currency", s.currency()), s.count())).toList(), true);
            amount.register(summary.stream().map(s -> MultiGauge.Row.of(Tags.of("currency", s.currency(),
                    "check", s.check()), s.absoluteDelta().doubleValue())).toList(), true);
            Instant now = clock.instant();
            oldestAgeSeconds.set(summary.stream().map(OpenSummary::oldestFirstSeenAt).min(Instant::compareTo)
                    .map(t -> Duration.between(t, now).toSeconds()).orElse(0L));
        } catch (RuntimeException e) {
            log.atDebug().addKeyValue("errorCode", e.getClass().getSimpleName()).log("reconciliation gauges not refreshed");
        }
    }
}
