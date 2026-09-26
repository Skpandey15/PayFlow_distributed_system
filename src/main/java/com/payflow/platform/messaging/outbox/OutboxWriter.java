package com.payflow.platform.messaging.outbox;

import com.payflow.platform.messaging.EnvelopeFactory;
import com.payflow.platform.messaging.OutgoingMessage;
import com.payflow.platform.messaging.PreparedMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Transactional Outbox writer: appends the message to the context's {@code outbox_event} table <b>in the
 * caller's database transaction</b>. The business change and the fact that it must be announced therefore
 * commit or roll back together, which removes the database/broker dual write (WP-01 finding M1).
 * Publication to Kafka happens later and independently ({@link OutboxRelay}).
 */
@Component
public class OutboxWriter {

    private static final Pattern OUTBOX_TABLE = Pattern.compile("^[a-z_]+\\.outbox_event$");

    private final JdbcTemplate jdbc;
    private final EnvelopeFactory envelopes;

    public OutboxWriter(JdbcTemplate jdbc, EnvelopeFactory envelopes) {
        this.jdbc = jdbc;
        this.envelopes = envelopes;
    }

    public UUID append(String outboxTable, String producer, OutgoingMessage message) {
        requireValidTable(outboxTable);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Outbox writes must join the business transaction");
        }
        PreparedMessage m = envelopes.prepare(producer, message);
        jdbc.update("insert into " + outboxTable + """
                         (event_id, aggregate_type, aggregate_id, topic, message_key, event_type, event_version,
                          envelope, correlation_id, traceparent, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)""",
                m.envelope().eventId(), m.envelope().aggregateType(), m.envelope().aggregateId(), m.topic(), m.key(),
                m.envelope().eventType(), m.envelope().eventVersion(), m.json(), m.envelope().correlationId(),
                m.traceparent(), Timestamp.from(m.envelope().occurredAt()));
        return m.envelope().eventId();
    }

    static void requireValidTable(String table) {
        if (!OUTBOX_TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException("Not an outbox table: " + table);
        }
    }
}
