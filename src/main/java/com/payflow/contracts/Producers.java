package com.payflow.contracts;

/** Logical producer identities. In production each maps to its own Kafka principal (SASL/mTLS) and ACL set. */
public final class Producers {

    public static final String PAYMENT = "payment-service";
    public static final String FRAUD = "fraud-service";
    public static final String ACCOUNT = "account-service";
    public static final String SETTLEMENT = "settlement-service";

    private Producers() {
    }
}
