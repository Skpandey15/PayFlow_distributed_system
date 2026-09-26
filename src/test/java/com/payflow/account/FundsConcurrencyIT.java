package com.payflow.account;

import com.payflow.account.application.port.in.DepositFundsUseCase;
import com.payflow.account.application.port.in.DepositFundsUseCase.DepositCommand;
import com.payflow.account.application.port.in.FundsCommandUseCase;
import com.payflow.account.application.port.in.FundsCommandUseCase.ReleaseFunds;
import com.payflow.account.application.port.in.FundsCommandUseCase.ReserveFunds;
import com.payflow.account.application.port.in.OpenAccountUseCase;
import com.payflow.account.application.port.in.OpenAccountUseCase.OpenAccountCommand;
import com.payflow.shared.application.Actor;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import com.payflow.support.Actors;
import com.payflow.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Funds control (WP-01 finding M2) under concurrency, against real PostgreSQL row locks. The Account context is
 * called directly (in-process) so the race is exact and not smoothed out by Kafka's sequential consumption.
 * The reservations are for synthetic payment ids, so the saga later dead-letters their replies as "unknown
 * payment", which is the correct behaviour for a reply with no saga.
 */
@IntegrationTest
class FundsConcurrencyIT {

    @Autowired
    FundsCommandUseCase funds;
    @Autowired
    OpenAccountUseCase accounts;
    @Autowired
    DepositFundsUseCase deposits;
    @Autowired
    JdbcTemplate jdbc;

    AccountId payer;
    AccountId payee;

    @BeforeEach
    void setUp() {
        Actor owner = Actors.customer("funds-" + UUID.randomUUID());
        payer = new AccountId(accounts.open(new OpenAccountCommand(owner, "Payer", "USD")).id());
        payee = new AccountId(accounts.open(new OpenAccountCommand(owner, "Payee", "USD")).id());
        deposits.deposit(new DepositCommand(new Actor("treasury", Set.of("funds:deposit")), payer, UUID.randomUUID(),
                Money.of("100.00", "USD")));
    }

    Money available() {
        return Money.of(jdbc.queryForObject("select available from account.account_balance where account_id = ?",
                java.math.BigDecimal.class, payer.value()), Money.currency("USD"));
    }

    Money reserved() {
        return Money.of(jdbc.queryForObject("select reserved from account.account_balance where account_id = ?",
                java.math.BigDecimal.class, payer.value()), Money.currency("USD"));
    }

    static <T> List<T> race(int threads, Callable<T> task) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    /** Failure 18: ten concurrent 30.00 reservations against 100.00 available. Exactly three can succeed. */
    @RepeatedTest(3)
    void concurrentReservationsNeverOverdraw() throws Exception {
        race(10, () -> {
            funds.reserve(new ReserveFunds(UUID.randomUUID(), payer, payee, Money.of("30.00", "USD")));
            return null;
        });

        List<String> statuses = jdbc.queryForList(
                "select status from account.funds_reservation where payer_account_id = ?", String.class, payer.value());
        assertThat(statuses).hasSize(10);
        assertThat(statuses.stream().filter("RESERVED"::equals)).hasSize(3);
        assertThat(jdbc.queryForList("select reason from account.funds_reservation where payer_account_id = ? and status = 'REJECTED'",
                String.class, payer.value())).containsOnly("INSUFFICIENT_FUNDS");
        assertThat(available()).isEqualTo(Money.of("10.00", "USD"));
        assertThat(reserved()).isEqualTo(Money.of("90.00", "USD"));
    }

    /** The same ReserveFunds command delivered concurrently (e.g. a recovery re-issue racing the original) holds funds once. */
    @Test
    void concurrentDuplicateReservationHoldsFundsOnce() throws Exception {
        UUID paymentId = UUID.randomUUID();
        race(8, () -> {
            funds.reserve(new ReserveFunds(paymentId, payer, payee, Money.of("25.00", "USD")));
            return null;
        });
        assertThat(reserved()).isEqualTo(Money.of("25.00", "USD"));
        assertThat(jdbc.queryForObject("select count(*) from account.funds_reservation where payment_id = ?",
                Integer.class, paymentId)).isEqualTo(1);
    }

    /** Failure 17: duplicate compensation (release) is a no-op the second time. */
    @Test
    void duplicateReleaseRestoresFundsOnce() {
        UUID paymentId = UUID.randomUUID();
        funds.reserve(new ReserveFunds(paymentId, payer, payee, Money.of("40.00", "USD")));
        ReleaseFunds release = new ReleaseFunds(paymentId, payer, Money.of("40.00", "USD"), "SETTLEMENT_DECLINED");

        funds.release(release);
        funds.release(release);

        assertThat(available()).isEqualTo(Money.of("100.00", "USD"));
        assertThat(reserved().isZero()).isTrue();
    }

    /** Ordering hazard: a release that overtakes its reservation leaves a tombstone, and the late reserve is refused. */
    @Test
    void releaseBeforeReserveLeavesTombstoneAndLateReserveIsRefused() {
        UUID paymentId = UUID.randomUUID();
        funds.release(new ReleaseFunds(paymentId, payer, Money.of("50.00", "USD"), "CANCELLED"));
        funds.reserve(new ReserveFunds(paymentId, payer, payee, Money.of("50.00", "USD")));

        assertThat(jdbc.queryForObject("select status from account.funds_reservation where payment_id = ?",
                String.class, paymentId)).isEqualTo("RELEASED");
        assertThat(available()).as("nothing held for a compensated payment").isEqualTo(Money.of("100.00", "USD"));
        assertThat(jdbc.queryForObject("select count(*) from account.outbox_event where aggregate_id = ?"
                + " and event_type = 'FundsReservationFailed'", Integer.class, paymentId.toString())).isEqualTo(1);
    }

    @Test
    void databaseRefusesNegativeBalancesEvenIfCodeIsBypassed() {
        assertThatThrownBy(() -> jdbc.update("update account.account_balance set available = -0.01 where account_id = ?",
                payer.value()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_balance_available_non_negative");
    }
}
