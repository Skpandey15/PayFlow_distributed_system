package com.payflow.platform.messaging.outbox;

import com.payflow.platform.messaging.MessagingMetrics;
import com.payflow.platform.messaging.PreparedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

/**
 * Polling publisher for one context's outbox.
 *
 * <pre>
 * BEGIN
 *   pg_try_advisory_xact_lock(outbox)        -- one active relay per outbox across all replicas
 *   SELECT unpublished ORDER BY id LIMIT n
 *   for each row in order:
 *       send to Kafka and wait for acks=all  -- idempotent producer
 *       UPDATE published_at                  -- only after the broker acknowledged
 *       on failure: record attempt, STOP     -- never skip ahead: preserves per-key order
 * COMMIT
 * </pre>
 *
 * Guarantees:
 * <ul>
 *   <li><b>No loss.</b> A row stays unpublished until the broker has acknowledged it.</li>
 *   <li><b>Possible duplicates.</b> A crash after the broker ack but before COMMIT re-sends the row, so consumers
 *       must deduplicate on {@code eventId}.</li>
 *   <li><b>Per-aggregate order.</b> There is a single writer per outbox, rows go out in id order, and the relay
 *       stops at the first failure.</li>
 * </ul>
 * Trade-off (documented in ADR-010): the relay transaction spans the Kafka sends. It touches only outbox rows
 * (no business rows, no user latency), is bounded by batch size × send timeout, and has its own timeout.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final String table;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final MessagingMetrics metrics;
    private final Clock clock;
    private final int batchSize;
    private final Duration sendTimeout;
    private final long lockKey;
    private final AtomicLong backlog = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();

    public OutboxRelay(String table, JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                       KafkaTemplate<String, String> kafka, MessagingMetrics metrics, Clock clock,
                       OutboxProperties properties) {
        OutboxWriter.requireValidTable(table);
        this.table = table;
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.metrics = metrics;
        this.clock = clock;
        this.batchSize = properties.batchSize();
        this.sendTimeout = properties.sendTimeout();
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setTimeout((int) Math.max(1, properties.relayTransactionTimeout().toSeconds()));
        CRC32 crc = new CRC32();
        crc.update(("payflow-outbox:" + table).getBytes(StandardCharsets.UTF_8));
        this.lockKey = crc.getValue();
        metrics.outboxGauges(table, backlog, oldestAgeSeconds);
    }

    record Row(long id, String eventId, String topic, String key, String eventType, int eventVersion, String envelope,
               String correlationId, String traceparent, String producer) {
    }

    /** Publishes at most one batch. Returns the number of rows published (0 if another replica holds the lock). */
    public int publishBatch() {
        Integer published = tx.execute(status -> {
            Boolean locked = jdbc.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, lockKey);
            if (!Boolean.TRUE.equals(locked)) {
                return 0;
            }
            List<Row> rows = jdbc.query("select id, event_id, topic, message_key, event_type, event_version, "
                            + "envelope::text as envelope, correlation_id, traceparent, envelope->>'producer' as producer from "
                            + table + " where published_at is null order by id limit ?",
                    (rs, i) -> new Row(rs.getLong("id"), rs.getString("event_id"), rs.getString("topic"),
                            rs.getString("message_key"), rs.getString("event_type"), rs.getInt("event_version"),
                            rs.getString("envelope"), rs.getString("correlation_id"), rs.getString("traceparent"),
                            rs.getString("producer")),
                    batchSize);
            int sent = 0;
            for (Row row : rows) {
                try {
                    kafka.send(PreparedMessage.toRecord(row.topic(), row.key(), row.envelope(), row.eventId(),
                                    row.eventType(), row.eventVersion(), row.producer(), row.correlationId(), row.traceparent()))
                            .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    jdbc.update("update " + table + " set published_at = ?, publish_attempts = publish_attempts + 1,"
                            + " last_error = null where id = ?", Timestamp.from(clock.instant()), row.id());
                    sent++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    recordFailure(row, e);
                    break;
                } catch (Exception e) {
                    recordFailure(row, e);
                    break;
                }
            }
            refreshBacklog();
            return sent;
        });
        if (published != null && published > 0) {
            metrics.published(table, published);
        }
        return published == null ? 0 : published;
    }

    private void recordFailure(Row row, Exception e) {
        String error = e.getClass().getSimpleName();
        jdbc.update("update " + table + " set publish_attempts = publish_attempts + 1, last_error = ? where id = ?",
                error, row.id());
        metrics.publishFailed(table);
        log.atWarn()
                .addKeyValue("outbox", table)
                .addKeyValue("eventId", row.eventId())
                .addKeyValue("eventType", row.eventType())
                .addKeyValue("topic", row.topic())
                .addKeyValue("errorCode", error)
                .log("outbox publication failed; will retry in order on next poll");
    }

    private void refreshBacklog() {
        jdbc.query("select count(*) as backlog, min(created_at) as oldest from " + table + " where published_at is null",
                rs -> {
                    backlog.set(rs.getLong("backlog"));
                    Timestamp oldest = rs.getTimestamp("oldest");
                    oldestAgeSeconds.set(oldest == null ? 0
                            : Math.max(0, Duration.between(oldest.toInstant(), clock.instant()).toSeconds()));
                });
    }

    /** Removes published rows past retention (they are also on the Kafka log for the topic retention period). */
    public int purgePublishedBefore(Instant cutoff) {
        return jdbc.update("delete from " + table + " where published_at is not null and published_at < ?",
                Timestamp.from(cutoff));
    }

    public long backlog() {
        refreshBacklogStandalone();
        return backlog.get();
    }

    private void refreshBacklogStandalone() {
        tx.executeWithoutResult(s -> refreshBacklog());
    }

    public String table() {
        return table;
    }
}
