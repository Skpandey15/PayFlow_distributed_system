package com.payflow.settlement.domain;

/** External rail over which funds move. Each rail is served by its own gateway strategy. */
public enum SettlementRail {
    CARD_NETWORK,
    UPI,
    BANK_TRANSFER
}
