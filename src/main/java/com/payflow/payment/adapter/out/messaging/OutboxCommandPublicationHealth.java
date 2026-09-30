package com.payflow.payment.adapter.out.messaging;

import com.payflow.payment.application.port.out.CommandPublicationHealthPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Oldest unpublished row of the payment outbox (which carries every saga command). Served by the partial index
 * {@code ix_payment_outbox_unpublished}: a min() over unpublished rows only, cheap even with a large backlog.
 */
@Component
class OutboxCommandPublicationHealth implements CommandPublicationHealthPort {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    OutboxCommandPublicationHealth(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Duration oldestUnpublishedCommandAge() {
        return jdbc.query("select created_at from payment.outbox_event where published_at is null order by id limit 1",
                        (rs, i) -> rs.getTimestamp(1)).stream().findFirst()
                .map(oldest -> Duration.between(oldest.toInstant(), clock.instant()))
                .orElse(Duration.ZERO);
    }
}
