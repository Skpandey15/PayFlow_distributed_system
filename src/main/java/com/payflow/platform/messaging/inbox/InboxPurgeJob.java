package com.payflow.platform.messaging.inbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Bounds the inbox (processed-event) tables. WP-03 found that WP-02 defined the purge but never scheduled it: the
 * inboxes grew without limit (1.8 M rows in payment.processed_event during the lab campaigns).
 *
 * <p>Retention is a <b>correctness</b> parameter, not housekeeping: the inbox deduplicates redeliveries and DLT
 * replays, and any message still on Kafka (topic retention 7 days) can come back. Retention must exceed topic
 * retention plus the replay window, hence 8 days. Anything older is absorbed by natural business keys (unique
 * reservation per payment, journal reference, saga step guards).
 */
@Component
class InboxPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(InboxPurgeJob.class);
    static final List<String> INBOXES = List.of("payment.processed_event", "account.processed_event",
            "ledger.processed_event");

    private final InboxStore inbox;
    private final Clock clock;
    private final Duration retention;

    InboxPurgeJob(InboxStore inbox, Clock clock, @Value("${payflow.messaging.inbox.retention:8d}") Duration retention) {
        this.inbox = inbox;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${payflow.messaging.inbox.purge-interval-ms:3600000}",
            initialDelayString = "${payflow.messaging.inbox.purge-interval-ms:3600000}")
    void purge() {
        for (String table : INBOXES) {
            try {
                int removed = inbox.purgeBefore(table, clock.instant().minus(retention));
                log.atDebug().addKeyValue("inbox", table).addKeyValue("removed", removed).log("inbox purged");
            } catch (RuntimeException e) {
                log.atWarn().addKeyValue("inbox", table).addKeyValue("errorCode", e.getClass().getSimpleName())
                        .log("inbox purge failed; retried next interval");
            }
        }
    }
}
