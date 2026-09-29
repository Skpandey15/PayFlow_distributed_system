package com.payflow.settlement.adapter.out.gateway;

import com.payflow.settlement.domain.SettlementRail;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * {@code payflow.settlement.rail.calls{rail, operation, outcome}}: every rail call's result in the port's vocabulary
 * (ACCEPTED, DECLINED, NOT_FOUND, or a failure code such as SETTLEMENT_RAIL_TIMEOUT). Latency comes from the HTTP
 * client observation ({@code http.client.requests}); circuit, retry and bulkhead state from Resilience4j's meters.
 * Low cardinality by construction: 3 rails × 3 operations × ~12 outcomes.
 */
public class RailCallMetrics {

    private final MeterRegistry registry;

    public RailCallMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void outcome(SettlementRail rail, String operation, String outcome) {
        Counter.builder("payflow.settlement.rail.calls").tag("rail", rail.name()).tag("operation", operation)
                .tag("outcome", outcome).register(registry).increment();
    }
}
