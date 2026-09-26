package com.payflow.account.domain;

public enum AccountStatus {
    /** May send and receive payments. */
    ACTIVE,
    /** Temporarily blocked (e.g. compliance hold); cannot participate in new payments. */
    FROZEN
}
