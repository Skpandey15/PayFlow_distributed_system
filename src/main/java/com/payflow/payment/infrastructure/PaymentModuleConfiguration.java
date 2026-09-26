package com.payflow.payment.infrastructure;

import com.payflow.payment.application.port.out.AccountLookupPort;
import com.payflow.payment.application.port.out.FraudAssessmentPort;
import com.payflow.payment.application.port.out.IdempotencyStorePort;
import com.payflow.payment.application.port.out.LedgerPostingPort;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.application.port.out.SettlementProviderPort;
import com.payflow.payment.application.usecase.AuthorizePaymentService;
import com.payflow.payment.application.usecase.CancelPaymentService;
import com.payflow.payment.application.usecase.CreatePaymentService;
import com.payflow.payment.application.usecase.PaymentQueryService;
import com.payflow.payment.application.usecase.ProcessPaymentService;
import com.payflow.payment.application.usecase.PurgeExpiredIdempotencyKeysService;
import com.payflow.shared.application.TransactionRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Composition root of the Payment context. Use-case classes are plain Java with constructor injection.
 * This is the only place that binds them to Spring, which keeps the application layer framework-free
 * and trivially unit-testable.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentProperties.class)
class PaymentModuleConfiguration {

    @Bean
    CreatePaymentService createPaymentService(PaymentRepositoryPort payments, IdempotencyStorePort idempotency,
                                              AccountLookupPort accounts, PaymentEventPublisherPort events,
                                              TransactionRunner tx, Clock clock, PaymentProperties properties) {
        return new CreatePaymentService(payments, idempotency, accounts, events, tx, clock,
                properties.idempotencyRetention());
    }

    @Bean
    PaymentQueryService paymentQueryService(PaymentRepositoryPort payments, TransactionRunner tx) {
        return new PaymentQueryService(payments, tx);
    }

    @Bean
    CancelPaymentService cancelPaymentService(PaymentRepositoryPort payments, PaymentEventPublisherPort events,
                                              TransactionRunner tx, Clock clock) {
        return new CancelPaymentService(payments, events, tx, clock);
    }

    @Bean
    AuthorizePaymentService authorizePaymentService(PaymentRepositoryPort payments, AccountLookupPort accounts,
                                                    FraudAssessmentPort fraud, PaymentEventPublisherPort events,
                                                    TransactionRunner tx, Clock clock) {
        return new AuthorizePaymentService(payments, accounts, fraud, events, tx, clock);
    }

    @Bean
    ProcessPaymentService processPaymentService(PaymentRepositoryPort payments, SettlementProviderPort settlement,
                                                LedgerPostingPort ledger, PaymentEventPublisherPort events,
                                                TransactionRunner tx, Clock clock) {
        return new ProcessPaymentService(payments, settlement, ledger, events, tx, clock);
    }

    @Bean
    PurgeExpiredIdempotencyKeysService purgeExpiredIdempotencyKeysService(IdempotencyStorePort idempotency,
                                                                          TransactionRunner tx, Clock clock) {
        return new PurgeExpiredIdempotencyKeysService(idempotency, tx, clock);
    }
}
