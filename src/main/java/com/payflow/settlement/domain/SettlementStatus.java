package com.payflow.settlement.domain;

public enum SettlementStatus {
    /** Recorded locally; submission to the rail is in flight or must be resumed. */
    PENDING,
    /** The rail accepted and settled the funds. */
    COMPLETED,
    /** The rail declined the instruction. */
    DECLINED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
