package com.payflow.ledger.domain;

/**
 * Side of a double-entry posting. Customer accounts are liability-style accounts: a CREDIT increases
 * the balance the platform owes the customer, and a DEBIT decreases it.
 */
public enum EntryDirection {
    DEBIT,
    CREDIT
}
