package com.payflow.contracts;

/**
 * Kafka topics of the PayFlow event backbone (see docs/architecture/KAFKA-TOPIC-CATALOG.md).
 *
 * <p>Ownership convention:
 * <ul>
 *   <li>{@code <context>.commands}: owned by the <em>receiving</em> context, which defines what it accepts.
 *       Only the Payment saga orchestrator may write to it.</li>
 *   <li>{@code <context>.events}: owned by the <em>producing</em> context, which is its single writer.</li>
 * </ul>
 * All payment-workflow messages are keyed by {@code paymentId}, so every message about one payment lands on
 * one partition, and Kafka's per-partition ordering then applies to that payment's lifecycle.
 */
public final class Topics {

    public static final String PAYMENT_EVENTS = "payment.events";
    public static final String FRAUD_COMMANDS = "fraud.commands";
    public static final String FRAUD_EVENTS = "fraud.events";
    public static final String FUNDS_COMMANDS = "funds.commands";
    public static final String FUNDS_EVENTS = "funds.events";
    public static final String SETTLEMENT_COMMANDS = "settlement.commands";
    public static final String SETTLEMENT_EVENTS = "settlement.events";

    private Topics() {
    }
}
