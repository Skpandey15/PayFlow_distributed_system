package com.payflow.payment.infrastructure;

import com.payflow.payment.application.port.out.AccountLookupPort;
import com.payflow.payment.application.port.out.CommandPublicationHealthPort;
import com.payflow.payment.application.port.out.IdempotencyStorePort;
import com.payflow.payment.application.port.out.ManualReviewAuditPort;
import com.payflow.payment.application.port.out.SettlementEvidencePort;
import com.payflow.payment.application.port.out.SettlementParkingPort;
import com.payflow.payment.application.port.out.PaymentEventPublisherPort;
import com.payflow.payment.application.port.out.PaymentRepositoryPort;
import com.payflow.payment.application.port.out.PaymentSagaRepositoryPort;
import com.payflow.payment.application.port.out.SagaCommandPort;
import com.payflow.payment.application.usecase.CancelPaymentService;
import com.payflow.payment.application.usecase.CreatePaymentService;
import com.payflow.payment.application.usecase.PaymentQueryService;
import com.payflow.payment.application.usecase.PaymentSagaService;
import com.payflow.payment.application.usecase.PurgeExpiredIdempotencyKeysService;
import com.payflow.payment.application.usecase.SagaPolicy;
import com.payflow.payment.application.usecase.ManualReviewService;
import com.payflow.payment.application.usecase.SagaMonitoringService;
import com.payflow.payment.application.usecase.SagaRecoveryService;
import com.payflow.platform.messaging.outbox.OutboxRelay;
import com.payflow.platform.messaging.outbox.OutboxRelayFactory;
import com.payflow.shared.application.TransactionRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Composition root of the Payment context. Use-case classes are plain Java with constructor injection. This is
 * the only place that binds them to Spring, which keeps the application layer framework-free and trivially
 * unit-testable.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PaymentProperties.class, SagaProperties.class})
class PaymentModuleConfiguration {

    @Bean
    CreatePaymentService createPaymentService(PaymentRepositoryPort payments, IdempotencyStorePort idempotency,
                                              AccountLookupPort accounts, PaymentSagaRepositoryPort sagas,
                                              SagaCommandPort commands, PaymentEventPublisherPort events,
                                              TransactionRunner tx, Clock clock, PaymentProperties properties) {
        return new CreatePaymentService(payments, idempotency, accounts, sagas, commands, events, tx, clock,
                properties.idempotencyRetention());
    }

    @Bean
    PaymentQueryService paymentQueryService(PaymentRepositoryPort payments, TransactionRunner tx) {
        return new PaymentQueryService(payments, tx);
    }

    @Bean
    CancelPaymentService cancelPaymentService(PaymentRepositoryPort payments, PaymentSagaRepositoryPort sagas,
                                              SagaCommandPort commands, PaymentEventPublisherPort events,
                                              TransactionRunner tx, Clock clock) {
        return new CancelPaymentService(payments, sagas, commands, events, tx, clock);
    }

    @Bean
    SagaMonitoringService sagaMonitoringService(PaymentSagaRepositoryPort sagas, TransactionRunner tx, Clock clock) {
        return new SagaMonitoringService(sagas, tx, clock);
    }

    @Bean
    PaymentSagaService paymentSagaService(PaymentRepositoryPort payments, PaymentSagaRepositoryPort sagas,
                                          SagaCommandPort commands, PaymentEventPublisherPort events,
                                          TransactionRunner tx, Clock clock) {
        return new PaymentSagaService(payments, sagas, commands, events, tx, clock);
    }

    @Bean
    SagaRecoveryService sagaRecoveryService(PaymentRepositoryPort payments, PaymentSagaRepositoryPort sagas,
                                            SagaCommandPort commands, PaymentEventPublisherPort events,
                                            TransactionRunner tx, Clock clock, SagaProperties properties,
                                            CommandPublicationHealthPort publication, SettlementParkingPort parking) {
        return new SagaRecoveryService(payments, sagas, commands, events, tx, clock, properties.toPolicy(), publication,
                parking);
    }

    @Bean
    ManualReviewService manualReviewService(PaymentRepositoryPort payments, PaymentSagaRepositoryPort sagas,
                                            SagaCommandPort commands, SettlementEvidencePort settlement,
                                            ManualReviewAuditPort audit, TransactionRunner tx, Clock clock,
                                            SagaProperties properties) {
        return new ManualReviewService(payments, sagas, commands, settlement, audit, tx, clock,
                properties.railIdempotencyWindow());
    }

    @Bean
    PurgeExpiredIdempotencyKeysService purgeExpiredIdempotencyKeysService(IdempotencyStorePort idempotency,
                                                                          TransactionRunner tx, Clock clock) {
        return new PurgeExpiredIdempotencyKeysService(idempotency, tx, clock);
    }

    /** Publishes payment.events and the saga's commands from the Payment context's own outbox. */
    @Bean
    OutboxRelay paymentOutboxRelay(OutboxRelayFactory relays) {
        return relays.forTable("payment.outbox_event");
    }
}
