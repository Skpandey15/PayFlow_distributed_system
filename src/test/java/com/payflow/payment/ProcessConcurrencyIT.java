package com.payflow.payment;

import com.payflow.account.application.port.in.OpenAccountUseCase;
import com.payflow.account.application.port.in.OpenAccountUseCase.OpenAccountCommand;
import com.payflow.ledger.application.port.in.GetAccountBalanceUseCase;
import com.payflow.payment.application.port.in.AuthorizePaymentUseCase;
import com.payflow.payment.application.port.in.AuthorizePaymentUseCase.AuthorizePaymentCommand;
import com.payflow.payment.application.port.in.AuthorizePaymentUseCase.CheckoutChannel;
import com.payflow.payment.application.port.in.CreatePaymentUseCase;
import com.payflow.payment.application.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import com.payflow.payment.application.port.in.GetPaymentUseCase;
import com.payflow.payment.application.port.in.ProcessPaymentUseCase;
import com.payflow.payment.domain.PaymentId;
import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.application.Actor;
import com.payflow.shared.application.ConcurrencyConflictException;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Double-submission to the settlement rail: several orchestrator instances (or retries) process the same
 * authorized payment at the same moment. Money must move exactly once. That means one settlement
 * instruction, one balanced journal entry, and ledger balances reflecting the amount once, whatever the
 * interleaving. Losers may see a retryable 409 (optimistic lock) but never a duplicate effect.
 */
@IntegrationTest
class ProcessConcurrencyIT {

    static final int CONCURRENT_PROCESSORS = 8;

    @Autowired
    OpenAccountUseCase openAccount;
    @Autowired
    CreatePaymentUseCase createPayment;
    @Autowired
    AuthorizePaymentUseCase authorizePayment;
    @Autowired
    ProcessPaymentUseCase processPayment;
    @Autowired
    GetPaymentUseCase getPayment;
    @Autowired
    GetAccountBalanceUseCase balances;
    @Autowired
    JdbcTemplate jdbc;

    @RepeatedTest(3)
    void concurrentProcessingMovesMoneyExactlyOnce() throws Exception {
        Actor alice = Actors.customer("alice-" + UUID.randomUUID());
        Actor bob = Actors.customer("bob-" + UUID.randomUUID());
        AccountId payer = new AccountId(openAccount.open(new OpenAccountCommand(alice, "Alice", "EUR")).id());
        AccountId payee = new AccountId(openAccount.open(new OpenAccountCommand(bob, "Bob", "EUR")).id());
        UUID id = createPayment.create(new CreatePaymentCommand(alice, UUID.randomUUID().toString(), payer, payee,
                Money.of("250.00", "EUR"), PaymentMethod.BANK_TRANSFER, "rent")).payment().id();
        PaymentId paymentId = new PaymentId(id);
        authorizePayment.authorize(new AuthorizePaymentCommand(Actors.processor(), paymentId,
                new CheckoutChannel("device", "198.51.100.1", "ua", "DE")));

        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        int conflicts = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_PROCESSORS)) {
            for (int i = 0; i < CONCURRENT_PROCESSORS; i++) {
                futures.add(pool.submit(() -> {
                    startGun.await();
                    return processPayment.process(Actors.processor(), paymentId);
                }));
            }
            startGun.countDown();
            for (Future<?> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).as("the only acceptable failure is a retryable optimistic-lock conflict")
                            .isInstanceOf(ConcurrencyConflictException.class);
                    conflicts++;
                }
            }
        }
        assertThat(conflicts).isLessThan(CONCURRENT_PROCESSORS);
        System.out.printf("[evidence] %d concurrent processors -> %d optimistic-lock conflicts, 1 settlement%n",
                CONCURRENT_PROCESSORS, conflicts);

        assertThat(getPayment.get(alice, paymentId).status()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(jdbc.queryForObject("select count(*) from settlement.settlement where payment_id = ?",
                Integer.class, id)).as("one settlement instruction").isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ledger.journal_entry where reference = ?",
                Integer.class, "payment:" + id + ":settlement")).as("one journal entry").isEqualTo(1);
        assertThat(balances.balance(Actors.ledgerReader(), payer, "EUR").balance()).isEqualTo(Money.of("-250.00", "EUR"));
        assertThat(balances.balance(Actors.ledgerReader(), payee, "EUR").balance()).isEqualTo(Money.of("250.00", "EUR"));
    }
}
