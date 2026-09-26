package com.payflow.platform.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Drives every context's {@link OutboxRelay}. It runs on every replica; the advisory lock inside each relay
 * makes exactly one replica publish a given outbox at a time.
 *
 * <p>{@link #pause()}/{@link #resume()} exist for operations (for example draining before a broker
 * migration) and for failure tests that need "events committed but not yet published".
 */
@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final List<OutboxRelay> relays;
    private final OutboxProperties properties;
    private final Clock clock;
    private volatile boolean paused;

    public OutboxRelayScheduler(List<OutboxRelay> relays, OutboxProperties properties, Clock clock) {
        this.relays = relays;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${payflow.messaging.outbox.poll-interval-ms:200}")
    public void relayAll() {
        if (paused) {
            return;
        }
        for (OutboxRelay relay : relays) {
            try {
                relay.publishBatch();
            } catch (RuntimeException e) {
                // e.g. PostgreSQL unreachable: nothing is lost, rows stay in the outbox until the next poll.
                log.atWarn().addKeyValue("outbox", relay.table()).addKeyValue("errorCode", e.getClass().getSimpleName())
                        .log("outbox relay poll failed");
            }
        }
    }

    @Scheduled(fixedDelayString = "${payflow.messaging.outbox.purge-interval-ms:3600000}",
            initialDelayString = "${payflow.messaging.outbox.purge-interval-ms:3600000}")
    public void purge() {
        for (OutboxRelay relay : relays) {
            int removed = relay.purgePublishedBefore(clock.instant().minus(properties.retention()));
            log.atDebug().addKeyValue("outbox", relay.table()).addKeyValue("removed", removed).log("outbox purged");
        }
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public List<OutboxRelay> relays() {
        return relays;
    }
}
