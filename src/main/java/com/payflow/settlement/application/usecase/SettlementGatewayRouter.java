package com.payflow.settlement.application.usecase;

import com.payflow.settlement.application.port.out.SettlementGatewayPort;
import com.payflow.settlement.domain.SettlementRail;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Selects the gateway strategy for a rail. Built once at startup and <b>fails fast</b> if any rail has no
 * gateway or has two, so a misconfiguration surfaces at deploy time and not on the first live payment.
 * Adding a rail means adding an enum constant and an adapter; this class does not change (Open/Closed).
 */
public class SettlementGatewayRouter {

    private final Map<SettlementRail, SettlementGatewayPort> gateways = new EnumMap<>(SettlementRail.class);

    public SettlementGatewayRouter(List<SettlementGatewayPort> available) {
        for (SettlementGatewayPort gateway : available) {
            if (gateways.putIfAbsent(gateway.rail(), gateway) != null) {
                throw new IllegalStateException("More than one gateway registered for rail " + gateway.rail());
            }
        }
        for (SettlementRail rail : SettlementRail.values()) {
            if (!gateways.containsKey(rail)) {
                throw new IllegalStateException("No settlement gateway registered for rail " + rail);
            }
        }
    }

    public SettlementGatewayPort gatewayFor(SettlementRail rail) {
        return gateways.get(rail);
    }
}
