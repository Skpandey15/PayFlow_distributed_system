package com.payflow.reconciliation.infrastructure;

import com.payflow.reconciliation.application.port.out.MismatchStorePort;
import com.payflow.reconciliation.application.port.out.ReconciliationSourcePort;
import com.payflow.reconciliation.application.usecase.ReconciliationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class ReconciliationConfiguration {

    @Bean
    ReconciliationService reconciliationService(ReconciliationSourcePort source, MismatchStorePort store, Clock clock,
                                                @Value("${payflow.reconciliation.grace:2m}") Duration grace) {
        return new ReconciliationService(source, store, clock, grace);
    }
}
