package com.payflow.settlement.infrastructure;

import com.payflow.platform.messaging.outbox.OutboxRelay;
import com.payflow.platform.messaging.outbox.OutboxRelayFactory;
import com.payflow.settlement.application.port.out.SettlementEventPublisherPort;
import com.payflow.settlement.application.port.out.SettlementRepositoryPort;
import com.payflow.settlement.application.usecase.SettlementGatewayRouter;
import com.payflow.settlement.application.usecase.SettlementOperationsService;
import com.payflow.settlement.application.usecase.SubmitSettlementService;
import com.payflow.shared.application.TransactionRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class SettlementModuleConfiguration {

    @Bean
    SettlementOperationsService settlementOperationsService(SettlementRepositoryPort settlements,
                                                            SettlementGatewayRouter router, TransactionRunner tx) {
        return new SettlementOperationsService(settlements, router, tx);
    }

    @Bean
    SubmitSettlementService submitSettlementService(SettlementRepositoryPort settlements, SettlementGatewayRouter router,
                                                    SettlementEventPublisherPort events, TransactionRunner tx,
                                                    Clock clock,
                                                    @Value("${payflow.settlement.parked.enabled:true}") boolean parking) {
        return new SubmitSettlementService(settlements, router, events, tx, clock, parking);
    }

    @Bean
    OutboxRelay settlementOutboxRelay(OutboxRelayFactory relays) {
        return relays.forTable("settlement.outbox_event");
    }
}
