package com.payflow.settlement.application.usecase;

import com.payflow.settlement.application.port.out.SettlementGatewayPort;
import com.payflow.settlement.domain.SettlementRail;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementGatewayRouterTest {

    static SettlementGatewayPort gateway(SettlementRail rail) {
        return new SettlementGatewayPort() {
            @Override
            public SettlementRail rail() {
                return rail;
            }

            @Override
            public GatewayResponse submit(GatewayInstruction instruction) {
                return GatewayResponse.accepted(rail.name());
            }
        };
    }

    @Test
    void routesEachRailToItsStrategy() {
        SettlementGatewayRouter router = new SettlementGatewayRouter(
                Arrays.stream(SettlementRail.values()).map(SettlementGatewayRouterTest::gateway).toList());
        for (SettlementRail rail : SettlementRail.values()) {
            assertThat(router.gatewayFor(rail).rail()).isEqualTo(rail);
        }
    }

    @Test
    void failsFastWhenARailHasNoGateway() {
        assertThatThrownBy(() -> new SettlementGatewayRouter(List.of(gateway(SettlementRail.UPI))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No settlement gateway registered");
    }

    @Test
    void failsFastOnAmbiguousGateways() {
        assertThatThrownBy(() -> new SettlementGatewayRouter(List.of(gateway(SettlementRail.UPI), gateway(SettlementRail.UPI))))
                .hasMessageContaining("More than one gateway");
    }
}
