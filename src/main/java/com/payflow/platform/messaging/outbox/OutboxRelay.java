package com.payflow.platform.messaging.outbox;

import com.payflow.platform.messaging.MessagingMetrics;
import com.payflow.platform.messaging.PreparedMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
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
    private final boolean pipelined;
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
        this.pipelined = properties.pipelined();
        this.sendTimeout = properties.sendTimeout();
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setTimeout((int) Math.max(1, properties.relayTransactionTimeout().toSeconds()));
        CRC32 crc = new CRC32();
        crc.update(("payflow-outbox:" + table).getBytes(StandardCharsets.UTF_8));
        this.lockKey = crc.getValue();
        metrics.outboxGauges(table, backlog, oldestAgeSeconds);
    }

    record Row(long id, String eventId, String topic, String key, String eventType, int eventVersion, String envelope,
               String correlationId, String traceparent, String producer, Instant createdAt) {
    }

    /** Publishes at most one batch. Returns the number of rows published (0 if another replica holds the lock). */
    public int publishBatch() {
        long batchStarted = System.nanoTime();
        Integer published = tx.execute(status -> {
            Boolean locked = jdbc.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, lockKey);
            if (!Boolean.TRUE.equals(locked)) {
                return 0;
            }
            List<Row> rows = jdbc.query("select id, event_id, topic, message_key, event_type, event_version, "
                            + "envelope::text as envelope, correlation_id, traceparent, envelope->>'producer' as producer, "
                            + "created_at from "
                            + table + " where published_at is null order by id limit ?",
                    (rs, i) -> new Row(rs.getLong("id"), rs.getString("event_id"), rs.getString("topic"),
                            rs.getString("message_key"), rs.getString("event_type"), rs.getInt("event_version"),
                            rs.getString("envelope"), rs.getString("correlation_id"), rs.getString("traceparent"),
                            rs.getString("producer"), rs.getTimestamp("created_at").toInstant()),
                    batchSize);
            int sent = pipelined ? sendPipelined(rows) : sendSequentially(rows);
            refreshBacklog();
            return sent;
        });
        if (published != null && published > 0) {
            metrics.published(table, published);
            metrics.time("payflow.outbox.relay.batch", Duration.ofNanos(System.nanoTime() - batchStarted), "outbox", table);
        }
        return published == null ? 0 : published;
    }

    /**
     * WP-02 mode, kept for A/B measurement: one record at a time, each waiting for its acknowledgement. Throughput is
     * bounded by 1 / (linger + broker round trip) per outbox, measured at about 70-100 events/s (WP-03-BASELINE.md).
     */
    private int sendSequentially(List<Row> rows) {
        int sent = 0;
        for (Row row : rows) {
            try {
                long sendStarted = System.nanoTime();
                kafka.send(record(row)).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
                metrics.time("payflow.outbox.send.latency", Duration.ofNanos(System.nanoTime() - sendStarted),
                        "outbox", table);
                markPublished(List.of(row));
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
        return sent;
    }

    /**
     * WP-03 mode: hand the whole batch to the producer in id order, then wait once for all acknowledgements. The
     * producer batches records per partition (linger.ms) and keeps up to 5 requests in flight per connection.
     *
     * <p>Ordering: the idempotent producer assigns per-partition sequence numbers in send order, and the broker
     * rejects a gap, so a later record of a partition cannot be written while an earlier one of the same partition
     * failed transiently. Only acknowledged rows are marked published (one UPDATE); failed rows stay unpublished
     * and are re-sent first on the next poll, because the scan is in id order. Records of other partitions are not
     * held back by one partition's failure. The residual risk (a non-retriable record-level error on an earlier
     * record of the same key, e.g. oversize) is documented in ADR-019 and surfaces as a stuck row and outbox-age alert.
     */
    private int sendPipelined(List<Row> rows) {
        long started = System.nanoTime();
        List<java.util.concurrent.CompletableFuture<?>> acks = new java.util.ArrayList<>(rows.size());
        for (Row row : rows) {
            try {
                acks.add(kafka.send(record(row)));
            } catch (RuntimeException e) {
                // e.g. buffer full / metadata unavailable within max.block.ms: stop handing over records
                acks.add(java.util.concurrent.CompletableFuture.failedFuture(e));
                break;
            }
        }
        List<Row> acknowledged = new java.util.ArrayList<>(rows.size());
        Row firstFailed = null;
        Exception firstError = null;
        long deadline = System.nanoTime() + sendTimeout.toNanos();
        for (int i = 0; i < acks.size(); i++) {
            try {
                acks.get(i).get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                acknowledged.add(rows.get(i));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                firstFailed = firstFailed == null ? rows.get(i) : firstFailed;
                firstError = firstError == null ? e : firstError;
                break;
            } catch (Exception e) {
                firstFailed = firstFailed == null ? rows.get(i) : firstFailed;
                firstError = firstError == null ? unwrap(e) : firstError;
            }
        }
        metrics.time("payflow.outbox.send.latency", Duration.ofNanos(System.nanoTime() - started), "outbox", table);
        markPublished(acknowledged);
        if (firstFailed != null) {
            recordFailure(firstFailed, firstError);
        }
        return acknowledged.size();
    }

    /** The future wraps the producer's failure; record the real cause (e.g. KafkaException, TimeoutException). */
    private static Exception unwrap(Exception e) {
        Throwable cause = e;
        while ((cause instanceof java.util.concurrent.ExecutionException
                || cause instanceof java.util.concurrent.CompletionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof Exception ex ? ex : e;
    }

    private ProducerRecord<String, String> record(Row row) {
        return PreparedMessage.toRecord(row.topic(), row.key(), row.envelope(), row.eventId(), row.eventType(),
                row.eventVersion(), row.producer(), row.correlationId(), row.traceparent());
    }

    private void markPublished(List<Row> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        jdbc.update("update " + table + " set published_at = ?, publish_attempts = publish_attempts + 1,"
                        + " last_error = null where id = any(?)", Timestamp.from(now),
                rows.stream().map(Row::id).toArray(Long[]::new));
        for (Row row : rows) {
            // Commit-to-broker delay of this event: the outbox publication SLI.
            metrics.time("payflow.outbox.publish.delay", Duration.between(row.createdAt(), now), "outbox", table);
        }
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
        return purgePublishedBefore(cutoff, Integer.MAX_VALUE);
    }

    /**
     * Deletes published rows older than {@code cutoff} in batches of {@code batch} (each its own short statement),
     * so a large purge never becomes one long transaction holding locks and producing a vacuum storm. WP-03 found
     * 7-day retention growing the payment outbox to 3.6 M rows / 3.8 GB during the lab campaigns, larger than the
     * database's memory, which degraded peak-load latency (TUNING-RESULTS §3).
     */
    public int purgePublishedBefore(Instant cutoff, int batch) {
        int total = 0;
        int removed;
        do {
            removed = jdbc.update("delete from " + table + " where id in (select id from " + table
                    + " where published_at is not null and published_at < ? order by id limit ?)",
                    Timestamp.from(cutoff), batch);
            total += removed;
        } while (removed >= batch);
        return total;
    }

    public long backlog() {
        refreshBacklogStandalone();
        return backlog.get();
    }

    private void refreshBacklogStandalone() {
        tx.executeWithoutResult(s -> refreshBacklog());
    }

    /** Age of the oldest unpublished row, read fresh (partial index; cheap). Zero when caught up. */
    public Duration oldestUnpublishedAge() {
        return jdbc.query("select created_at from " + table + " where published_at is null order by id limit 1",
                        (rs, i) -> rs.getTimestamp(1)).stream().findFirst()
                .map(t -> Duration.between(t.toInstant(), clock.instant()))
                .orElse(Duration.ZERO);
    }

    public int batchSize() {
        return batchSize;
    }

    public String table() {
        return table;
    }
}
