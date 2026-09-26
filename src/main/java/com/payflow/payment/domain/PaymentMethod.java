package com.payflow.payment.domain;

/** How the payer funds the payment. Selects the settlement rail (Strategy) in the Settlement context. */
public enum PaymentMethod {
    CARD,
    UPI,
    BANK_TRANSFER
}
