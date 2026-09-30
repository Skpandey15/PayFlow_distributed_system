package com.payflow.account.adapter.out.persistence;

import com.payflow.account.application.port.out.DepositRepositoryPort;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/** Deposits are insert-only facts; plain JDBC is simpler than an entity with no behaviour. */
@Component
class JdbcDepositRepositoryAdapter implements DepositRepositoryPort {

    private final JdbcTemplate jdbc;

    JdbcDepositRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DepositRecord> find(UUID depositId) {
        return jdbc.query("select id, account_id, amount, currency, created_by, created_at from account.funds_deposit where id = ?",
                        (rs, i) -> new DepositRecord(rs.getObject("id", UUID.class),
                                new AccountId(rs.getObject("account_id", UUID.class)),
                                Money.of(rs.getBigDecimal("amount"), Money.currency(rs.getString("currency"))),
                                rs.getString("created_by"), rs.getTimestamp("created_at").toInstant()),
                        depositId)
                .stream().findFirst();
    }

    @Override
    public void add(DepositRecord d) {
        jdbc.update("insert into account.funds_deposit (id, account_id, amount, currency, created_by, created_at) values (?, ?, ?, ?, ?, ?)",
                d.depositId(), d.accountId().value(), d.amount().amount(), d.amount().currencyCode(), d.createdBy(),
                Timestamp.from(d.createdAt()));
    }
}
