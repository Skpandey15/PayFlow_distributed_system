package com.payflow.platform.messaging.inbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Idempotent-consumer inbox: one row per (consumer, eventId) in the consuming context's own schema.
 *
 * <p>{@link #tryClaim} runs <b>inside</b> the business transaction. Its outcomes:
 * <ul>
 *   <li>First delivery: the INSERT succeeds; the business change and the claim commit together.</li>
 *   <li>Redelivery after commit (for example a crash before the offset commit): ON CONFLICT, 0 rows, so the caller skips.</li>
 *   <li>Concurrent duplicate (rebalance overlap): the second INSERT blocks on the primary key until the first
 *       transaction ends. If that one committed, the second is a duplicate; if it rolled back, the second
 *       proceeds. The database, not the application, is the correctness boundary.</li>
 * </ul>
 */
@Component
public class InboxStore {

    private static final Pattern INBOX_TABLE = Pattern.compile("^[a-z_]+\\.processed_event$");

    private final JdbcTemplate jdbc;

    public InboxStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tryClaim(String inboxTable, String consumer, UUID eventId, Instant now) {
        requireValidTable(inboxTable);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Inbox claims must join the business transaction");
        }
        return jdbc.update("insert into " + inboxTable + " (consumer, event_id, processed_at) values (?, ?, ?)"
                + " on conflict do nothing", consumer, eventId, Timestamp.from(now)) == 1;
    }

    /** Deduplication window = retention. Older redeliveries must be absorbed by natural business keys. */
    public int purgeBefore(String inboxTable, Instant cutoff) {
        requireValidTable(inboxTable);
        int total = 0;
        int removed;
        do { // bounded batches: short statements, no long lock or vacuum spike
            removed = jdbc.update("delete from " + inboxTable + " where ctid in (select ctid from " + inboxTable
                    + " where processed_at < ? limit 5000)", Timestamp.from(cutoff));
            total += removed;
        } while (removed >= 5000);
        return total;
    }

    private static void requireValidTable(String table) {
        if (!INBOX_TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException("Not an inbox table: " + table);
        }
    }
}
