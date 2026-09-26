package com.payflow.payment;

import com.payflow.account.application.port.in.OpenAccountUseCase;
import com.payflow.account.application.port.in.OpenAccountUseCase.OpenAccountCommand;
import com.payflow.payment.application.port.in.CreatePaymentUseCase;
import com.payflow.payment.application.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import com.payflow.payment.application.port.in.CreatePaymentUseCase.CreatePaymentResult;
import com.payflow.payment.domain.PaymentMethod;
import com.payflow.shared.application.Actor;
import com.payflow.shared.domain.AccountId;
import com.payflow.shared.domain.Money;
import com.payflow.support.Actors;
import com.payflow.support.IntegrationTest;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The classic double-charge scenario: a client times out and retries while the first request is still in
 * flight, or a load balancer duplicates a request. N identical requests with the same Idempotency-Key are
 * released at the same instant. Check-then-insert in application code alone would let several pass the
 * "does the key exist?" check. The PostgreSQL primary key serialises them, so exactly one payment is created
 * and every caller receives that same payment.
 */
@IntegrationTest
class IdempotencyConcurrencyIT {

    static final int CONCURRENT_REQUESTS = 16;

    @Autowired
    CreatePaymentUseCase createPayment;
    @Autowired
    OpenAccountUseCase openAccount;
    @Autowired
    JdbcTemplate jdbc;

    @RepeatedTest(3)
    void concurrentDuplicatesCreateExactlyOnePayment() throws Exception {
        Actor alice = Actors.customer("alice-" + UUID.randomUUID());
        Actor bob = Actors.customer("bob-" + UUID.randomUUID());
        AccountId payer = new AccountId(openAccount.open(new OpenAccountCommand(alice, "Alice", "USD")).id());
        AccountId payee = new AccountId(openAccount.open(new OpenAccountCommand(bob, "Bob", "USD")).id());
        String key = "retry-storm-" + UUID.randomUUID();
        CreatePaymentCommand command = new CreatePaymentCommand(alice, key, payer, payee, Money.of("99.99", "USD"),
                PaymentMethod.CARD, "order-7");

        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<CreatePaymentResult>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    startGun.await();
                    return createPayment.create(command);
                }));
            }
            startGun.countDown();
            List<CreatePaymentResult> results = new ArrayList<>();
            for (Future<CreatePaymentResult> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS)); // every caller succeeds, nobody gets an error
            }

            assertThat(results).extracting(r -> r.payment().id()).as("all callers see the same payment").containsOnly(
                    results.getFirst().payment().id());
            assertThat(results).filteredOn(r -> !r.replayed()).as("exactly one request created it").hasSize(1);
        }

        Integer paymentRows = jdbc.queryForObject(
                "select count(*) from payment.payment where initiated_by = ?", Integer.class, alice.subject());
        assertThat(paymentRows).as("exactly one row in the database").isEqualTo(1);
    }
}
