package com.payflow.payment.adapter.out.persistence;

import com.payflow.payment.application.port.out.ManualReviewAuditPort;
import com.payflow.payment.domain.PaymentId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Append-only: this adapter has no update or delete path, and the runtime DB role has no such privilege either. */
@Component
class JdbcManualReviewAuditAdapter implements ManualReviewAuditPort {

    private static final String COLUMNS = "id, idempotency_key, payment_id, saga_id, decision, escalated_from, reason, "
            + "ticket_reference, operator_subject, evidence::text as evidence, correlation_id, decided_at";
    private static final RowMapper<DecisionRecord> ROW = (rs, i) -> new DecisionRecord(
            rs.getObject("id", UUID.class), rs.getString("idempotency_key"),
            new PaymentId(rs.getObject("payment_id", UUID.class)), rs.getObject("saga_id", UUID.class),
            rs.getString("decision"), rs.getString("escalated_from"), rs.getString("reason"),
            rs.getString("ticket_reference"), rs.getString("operator_subject"), rs.getString("evidence"),
            rs.getString("correlation_id"), rs.getTimestamp("decided_at").toInstant());

    private final JdbcTemplate jdbc;

    JdbcManualReviewAuditAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(DecisionRecord r) {
        jdbc.update("insert into payment.manual_review_decision (id, idempotency_key, payment_id, saga_id, decision, "
                        + "escalated_from, reason, ticket_reference, operator_subject, evidence, correlation_id, decided_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
                r.id(), r.idempotencyKey(), r.paymentId().value(), r.sagaId(), r.decision(), r.escalatedFrom(),
                r.reason(), r.ticketReference(), r.operatorSubject(), r.evidenceJson(), r.correlationId(),
                Timestamp.from(r.decidedAt()));
    }

    @Override
    public Optional<DecisionRecord> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query("select " + COLUMNS + " from payment.manual_review_decision where idempotency_key = ?", ROW,
                idempotencyKey).stream().findFirst();
    }

    @Override
    public List<DecisionRecord> findByPayment(PaymentId paymentId) {
        return jdbc.query("select " + COLUMNS + " from payment.manual_review_decision where payment_id = ? "
                + "order by decided_at", ROW, paymentId.value());
    }
}
