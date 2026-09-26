package com.payflow.account.infrastructure;

import com.payflow.account.application.port.out.AccountRepositoryPort;
import com.payflow.account.application.usecase.AccountService;
import com.payflow.shared.application.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class AccountModuleConfiguration {

    @Bean
    AccountService accountService(AccountRepositoryPort accounts, TransactionRunner tx, Clock clock) {
        return new AccountService(accounts, tx, clock);
    }
}
