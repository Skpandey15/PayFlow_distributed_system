package com.payflow.ledger.infrastructure;

import com.payflow.ledger.application.port.out.JournalEntryRepositoryPort;
import com.payflow.ledger.application.usecase.LedgerService;
import com.payflow.shared.application.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class LedgerModuleConfiguration {

    @Bean
    LedgerService ledgerService(JournalEntryRepositoryPort journal, TransactionRunner tx, Clock clock) {
        return new LedgerService(journal, tx, clock);
    }
}
