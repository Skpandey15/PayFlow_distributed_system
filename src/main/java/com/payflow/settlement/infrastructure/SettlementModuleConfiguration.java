package com.payflow.settlement.infrastructure;

import com.payflow.settlement.application.port.out.SettlementGatewayPort;
import com.payflow.settlement.application.port.out.SettlementRepositoryPort;
import com.payflow.settlement.application.usecase.SettlementGatewayRouter;
import com.payflow.settlement.application.usecase.SubmitSettlementService;
import com.payflow.shared.application.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
class SettlementModuleConfiguration {

    /** All rail adapters on the classpath are collected here. The router fails startup if a rail is missing. */
    @Bean
    SettlementGatewayRouter settlementGatewayRouter(List<SettlementGatewayPort> gateways) {
        return new SettlementGatewayRouter(gateways);
    }

    @Bean
    SubmitSettlementService submitSettlementService(SettlementRepositoryPort settlements, SettlementGatewayRouter router,
                                                    TransactionRunner tx, Clock clock) {
        return new SubmitSettlementService(settlements, router, tx, clock);
    }
}
