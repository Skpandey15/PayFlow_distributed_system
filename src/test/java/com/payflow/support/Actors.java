package com.payflow.support;

import com.payflow.shared.application.Actor;

import java.util.Set;

/** Canonical callers used across tests. Scopes mirror the Keycloak realm in deploy/keycloak. */
public final class Actors {

    public static final String CUSTOMER_SCOPES = "payments:read payments:write accounts:read accounts:write";
    public static final String PROCESSOR_SCOPES = "payments:process";

    private Actors() {
    }

    public static Actor customer(String subject) {
        return new Actor(subject, Set.of("payments:read", "payments:write", "accounts:read", "accounts:write"));
    }

    public static Actor processor() {
        return new Actor("svc-payment-orchestrator", Set.of("payments:process"));
    }

    public static Actor ledgerReader() {
        return new Actor("svc-reconciliation", Set.of("ledger:read"));
    }
}
