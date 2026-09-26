package com.payflow.account.infrastructure;

import com.payflow.account.application.port.out.AccountBalanceRepositoryPort;
import com.payflow.account.application.port.out.AccountRepositoryPort;
import com.payflow.account.application.port.out.DepositRepositoryPort;
import com.payflow.account.application.port.out.FundsEventPublisherPort;
import com.payflow.account.application.port.out.FundsReservationRepositoryPort;
import com.payflow.account.application.usecase.AccountService;
import com.payflow.account.application.usecase.FundsService;
import com.payflow.platform.messaging.outbox.OutboxRelay;
import com.payflow.platform.messaging.outbox.OutboxRelayFactory;
import com.payflow.shared.application.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class AccountModuleConfiguration {

    @Bean
    AccountService accountService(AccountRepositoryPort accounts, AccountBalanceRepositoryPort balances,
                                  TransactionRunner tx, Clock clock) {
        return new AccountService(accounts, balances, tx, clock);
    }

    @Bean
    FundsService fundsService(AccountRepositoryPort accounts, AccountBalanceRepositoryPort balances,
                              FundsReservationRepositoryPort reservations, DepositRepositoryPort deposits,
                              FundsEventPublisherPort events, TransactionRunner tx, Clock clock) {
        return new FundsService(accounts, balances, reservations, deposits, events, tx, clock);
    }

    /** Publishes {@code funds.events} from the Account context's own outbox. */
    @Bean
    OutboxRelay accountOutboxRelay(OutboxRelayFactory relays) {
        return relays.forTable("account.outbox_event");
    }
}
