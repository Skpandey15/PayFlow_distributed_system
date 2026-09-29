package com.payflow.reconciliation.adapter.out.persistence;

import com.payflow.reconciliation.application.port.in.ReconciliationUseCase.MismatchView;
import com.payflow.reconciliation.application.port.in.ReconciliationUseCase.OpenSummary;
import com.payflow.reconciliation.application.port.out.MismatchStorePort;
import com.payflow.reconciliation.domain.Finding;
import com.payflow.shared.application.PageQuery;
import com.payflow.shared.application.PageResult;
import com.payflow.shared.domain.Identifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
class JdbcMismatchStore implements MismatchStorePort {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    JdbcMismatchStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public void recordRun(UUID runId, Instant startedAt, Instant finishedAt, long recordsChecked, List<Finding> findings) {
        Timestamp now = Timestamp.from(finishedAt);
        tx.executeWithoutResult(s -> {
            jdbc.update("insert into reconciliation.run (id, started_at, finished_at, records_checked, findings) "
                    + "values (?, ?, ?, ?, ?)", runId, Timestamp.from(startedAt), now, recordsChecked, findings.size());
            for (Finding f : findings) {
                int updated = jdbc.update("update reconciliation.mismatch set times_seen = times_seen + 1, "
                                + "last_seen_at = ?, last_run_id = ?, expected = ?, actual = ? "
                                + "where check_name = ? and subject_id = ? and status = 'OPEN'",
                        now, runId, f.expected(), f.actual(), f.check().name(), f.subjectId());
                if (updated == 0) {
                    jdbc.update("insert into reconciliation.mismatch (id, check_name, severity, subject_type, subject_id, "
                                    + "currency, expected, actual, times_seen, first_seen_at, last_seen_at, last_run_id, status) "
                                    + "values (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, 'OPEN')",
                            Identifiers.timeOrderedUuid(), f.check().name(), f.check().severity().name(), f.subjectType(),
                            f.subjectId(), f.currency(), f.expected(), f.actual(), now, now, runId);
                }
            }
            // Drift no longer observed is closed (the finding record, not money: nothing financial is modified here).
            jdbc.update("update reconciliation.mismatch set status = 'RESOLVED', resolved_at = ? "
                    + "where status = 'OPEN' and last_run_id <> ?", now, runId);
        });
    }

    @Override
    public PageResult<MismatchView> open(PageQuery page) {
        List<MismatchView> items = jdbc.query("select id, check_name, severity, subject_type, subject_id, currency, "
                        + "expected, actual, times_seen, first_seen_at, last_seen_at from reconciliation.mismatch "
                        + "where status = 'OPEN' order by severity, first_seen_at limit ? offset ?",
                (rs, i) -> new MismatchView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getInt(9),
                        rs.getTimestamp(10).toInstant(), rs.getTimestamp(11).toInstant()),
                page.size(), (long) page.page() * page.size());
        Long total = jdbc.queryForObject("select count(*) from reconciliation.mismatch where status = 'OPEN'", Long.class);
        return new PageResult<>(items, page.page(), page.size(), total == null ? 0 : total);
    }

    @Override
    public List<OpenSummary> openSummary() {
        return jdbc.query("select check_name, severity, times_seen >= 2 as confirmed, count(*), min(first_seen_at), "
                        + "coalesce(currency, 'n/a'), sum(abs(coalesce(expected, 0) - coalesce(actual, 0))) "
                        + "from reconciliation.mismatch where status = 'OPEN' "
                        + "group by check_name, severity, times_seen >= 2, coalesce(currency, 'n/a')",
                (rs, i) -> new OpenSummary(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getLong(4),
                        rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getBigDecimal(7)));
    }
}
