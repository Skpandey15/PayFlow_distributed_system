package com.payflow.payment.domain.saga;

/** Why the saga is releasing held funds; it decides the terminal step once the release is confirmed. */
public enum CompensationReason {
    /** The rail declined the settlement → saga FAILED. */
    SETTLEMENT_DECLINED,
    /** The payer cancelled while funds were (possibly) held → saga CANCELLED. */
    CANCELLED,
    /** Funds reservation did not answer in time → saga REJECTED. */
    TIMEOUT
}
