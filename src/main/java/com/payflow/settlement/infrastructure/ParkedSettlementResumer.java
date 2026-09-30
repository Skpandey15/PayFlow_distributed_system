package com.payflow.settlement.infrastructure;

import com.payflow.platform.messaging.MessagingMetrics;
import com.payflow.settlement.application.port.in.ResumeParkedSettlementsUseCase;
import com.payflow.settlement.application.port.in.ResumeParkedSettlementsUseCase.ResumeReport;
import com.payflow.settlement.domain.SettlementRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Re-submits settlements parked while their rail's circuit was open (review R-2). Runs on every replica: the claim
 * (SKIP LOCKED plus an idle window) gives each parked settlement to one replica at a time. Rails are independent: a
 * card outage leaves the UPI and bank backlogs untouched, and while the card circuit is open, each run costs one
 * fast-failing call for that rail.
 *
 * <p>The resume rate is <b>bounded</b> (one batch per rail per interval, default 10/s per rail), on purpose. Once
 * a rail recovers, every parked settlement is followed by capture, ledger and saga work. An unbounded drain in F-07
 * (about 2,200 parked) saturated the connection pool (47 waiting, 4.1 s acquire) and pushed acceptance p99 to 2.9 s.
 * Recovery must not crowd out new payments. The rate trade-off is measured in TUNING-RESULTS §10.
 */
@Component
@ConditionalOnProperty(name = "payflow.settlement.parked.enabled", havingValue = "true", matchIfMissing = true)
class ParkedSettlementResumer {

    private static final Logger log = LoggerFactory.getLogger("payflow.settlement.parked");

    private final ResumeParkedSettlementsUseCase resumer;
    private final MessagingMetrics metrics;
    private final int batchSize;
    private final Map<SettlementRail, Boolean> backlog = new EnumMap<>(SettlementRail.class);

    ParkedSettlementResumer(ResumeParkedSettlementsUseCase resumer, MessagingMetrics metrics,
                            @Value("${payflow.settlement.parked.batch-size:10}") int batchSize) {
        this.resumer = resumer;
        this.metrics = metrics;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${payflow.settlement.parked.resume-interval-ms:1000}", initialDelay = 5000)
    void resume() {
        for (SettlementRail rail : SettlementRail.values()) {
            try {
                resume(rail);
            } catch (RuntimeException e) {
                // Database unreachable or a concurrent reply won the optimistic lock: the claim expires, next run retries.
                log.atWarn().addKeyValue("rail", rail).addKeyValue("errorCode", e.getClass().getSimpleName())
                        .log("parked settlement resume run failed");
            }
        }
    }

    private void resume(SettlementRail rail) {
        ResumeReport report = resumer.resumeParked(rail, batchSize);
        if (report.answered() > 0) {
            metrics.count("payflow.settlement.resumed", report.answered(), "rail", rail.name());
        }
        logTransition(rail, report);
    }

    /** One log line per change (circuit refused a parked backlog / backlog flowing again), not one per run. */
    private void logTransition(SettlementRail rail, ResumeReport report) {
        if (report.claimed() == 0) {
            return;
        }
        boolean refused = report.stillParked();
        if (refused != Boolean.TRUE.equals(backlog.put(rail, refused))) {
            (refused ? log.atWarn() : log.atInfo()).addKeyValue("rail", rail).addKeyValue("resumed", report.answered())
                    .log(refused ? "parked settlements waiting: rail circuit still open" : "parked settlements resuming");
        }
    }
}
