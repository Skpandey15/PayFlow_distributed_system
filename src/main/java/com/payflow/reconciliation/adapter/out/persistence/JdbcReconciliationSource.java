package com.payflow.reconciliation.adapter.out.persistence;

import com.payflow.reconciliation.application.port.out.ReconciliationSourcePort;
import com.payflow.reconciliation.domain.Finding;
import com.payflow.reconciliation.domain.ReconciliationCheck;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.payflow.reconciliation.domain.ReconciliationCheck.*;

/**
 * Set-based SQL over the authoritative tables of four contexts. This is the one documented exception to "a context
 * reads only its own schema" (WP-03 review): reconciliation is, by definition, a comparison ACROSS independently
 * written records, and doing it through each context's use cases would mean millions of calls and no consistent view.
 * It is strictly read-only (READ ONLY transaction); on extraction it moves to a reporting replica or warehouse copy.
 *
 * <p>All checks run in ONE {@code REPEATABLE READ, READ ONLY} transaction, so every invariant is evaluated against
 * the same snapshot (no false mismatch from comparing table A at t1 with table B at t2). A transaction-scoped
 * advisory lock makes one replica the runner.
 */
@Component
class JdbcReconciliationSource implements ReconciliationSourcePort {

    private static final long LOCK_KEY = 0x7265636f6e63L; // "reconc"

    private final JdbcTemplate jdbc;
    private final TransactionTemplate snapshot;

    JdbcReconciliationSource(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.snapshot = new TransactionTemplate(transactionManager);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.setReadOnly(true);
        snapshot.setTimeout(120);
    }

    private record Check(ReconciliationCheck check, String countSql, String findSql, boolean usesCutoff) {
    }

    private static final List<Check> CHECKS = List.of(
            new Check(RESERVED_MATCHES_RESERVATIONS, "select count(*) from account.account_balance", """
                    select 'ACCOUNT', b.account_id::text, b.currency, coalesce(r.total, 0), b.reserved
                      from account.account_balance b
                      left join (select payer_account_id, sum(amount) total from account.funds_reservation
                                  where status = 'RESERVED' group by payer_account_id) r
                        on r.payer_account_id = b.account_id
                     where b.reserved <> coalesce(r.total, 0)""", false),
            new Check(LEDGER_MATCHES_BALANCE, "select count(*) from account.account_balance where updated_at < ?", """
                    select 'ACCOUNT', b.account_id::text, b.currency, b.available + b.reserved, coalesce(l.balance, 0)
                      from account.account_balance b
                      left join (select account_id, currency,
                                        sum(case direction when 'CREDIT' then amount else -amount end) balance
                                   from ledger.ledger_entry group by account_id, currency) l
                        on l.account_id = b.account_id and l.currency = b.currency
                     where b.updated_at < ? and b.available + b.reserved <> coalesce(l.balance, 0)""", true),
            new Check(CAPTURE_POSTED_TO_LEDGER,
                    "select count(*) from account.funds_reservation where status = 'CAPTURED' and updated_at < ?", """
                    select 'PAYMENT', r.payment_id::text, r.currency, r.amount, 0
                      from account.funds_reservation r
                      left join ledger.journal_entry j on j.reference = 'payment:' || r.payment_id || ':settlement'
                     where r.status = 'CAPTURED' and r.updated_at < ? and j.id is null""", true),
            new Check(LEDGER_POSTING_HAS_CAPTURE,
                    "select count(*) from ledger.journal_entry where reference like 'payment:%:settlement' and posted_at < ?", """
                    select 'PAYMENT', substring(j.reference from 9 for 36), j.currency, 0,
                           (select sum(e.amount) from ledger.ledger_entry e
                             where e.journal_entry_id = j.id and e.direction = 'DEBIT')
                      from ledger.journal_entry j
                      left join account.funds_reservation r
                        on 'payment:' || r.payment_id || ':settlement' = j.reference and r.status = 'CAPTURED'
                     where j.reference like 'payment:%:settlement' and j.posted_at < ? and r.id is null""", true),
            new Check(DEPOSIT_POSTED_TO_LEDGER, "select count(*) from account.funds_deposit where created_at < ?", """
                    select 'DEPOSIT', d.id::text, d.currency, d.amount, 0
                      from account.funds_deposit d
                      left join ledger.journal_entry j on j.reference = 'deposit:' || d.id
                     where d.created_at < ? and j.id is null""", true),
            new Check(SETTLED_PAYMENT_HAS_COMPLETED_SETTLEMENT,
                    "select count(*) from payment.payment where status = 'SETTLED'", """
                    select 'PAYMENT', p.id::text, p.currency, p.amount, coalesce(s.amount, 0)
                      from payment.payment p
                      left join settlement.settlement s on s.payment_id = p.id and s.status = 'COMPLETED'
                     where p.status = 'SETTLED' and s.id is null""", false),
            new Check(COMPLETED_SETTLEMENT_HAS_SETTLED_PAYMENT,
                    "select count(*) from settlement.settlement where status = 'COMPLETED' and updated_at < ?", """
                    select 'PAYMENT', s.payment_id::text, s.currency, s.amount, 0
                      from settlement.settlement s
                      join payment.payment p on p.id = s.payment_id
                      join payment.payment_saga g on g.payment_id = p.id
                     where s.status = 'COMPLETED' and s.updated_at < ? and p.status <> 'SETTLED'
                       and g.step not in ('AWAITING_CAPTURE', 'MANUAL_REVIEW')""", true),
            new Check(NO_HOLD_ON_FINISHED_PAYMENT,
                    "select count(*) from account.funds_reservation where status = 'RESERVED'", """
                    select 'PAYMENT', r.payment_id::text, r.currency, 0, r.amount
                      from account.funds_reservation r
                      join payment.payment p on p.id = r.payment_id
                      join payment.payment_saga g on g.payment_id = p.id
                     where r.status = 'RESERVED' and p.status in ('REJECTED', 'FAILED', 'CANCELLED')
                       and g.step not in ('COMPENSATING', 'MANUAL_REVIEW') and p.updated_at < ?""", true),
            new Check(LEDGER_ZERO_SUM, "select count(distinct currency) from ledger.ledger_entry", """
                    select 'LEDGER', currency, currency, 0,
                           sum(case direction when 'CREDIT' then amount else -amount end)
                      from ledger.ledger_entry group by currency
                    having sum(case direction when 'CREDIT' then amount else -amount end) <> 0""", false));

    @Override
    public Optional<Snapshot> evaluate(Instant settledBefore) {
        Timestamp cutoff = Timestamp.from(settledBefore);
        return Optional.ofNullable(snapshot.execute(status -> {
            Boolean locked = jdbc.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY);
            if (!Boolean.TRUE.equals(locked)) {
                return null;
            }
            Map<ReconciliationCheck, Long> checked = new EnumMap<>(ReconciliationCheck.class);
            List<Finding> findings = new ArrayList<>();
            for (Check c : CHECKS) {
                Object[] args = c.usesCutoff() ? new Object[]{cutoff} : new Object[0];
                checked.put(c.check(), jdbc.queryForObject(c.countSql(), Long.class, c.countSql().contains("?") ? args : new Object[0]));
                findings.addAll(jdbc.query(c.findSql(), (rs, i) -> new Finding(c.check(), rs.getString(1),
                        rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5)), args));
            }
            return new Snapshot(checked, findings);
        }));
    }
}
