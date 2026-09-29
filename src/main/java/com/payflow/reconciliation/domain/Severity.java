package com.payflow.reconciliation.domain;

/** CRITICAL: money is misstated or moved without a matching record. HIGH: a record is missing or funds are stuck. */
public enum Severity { CRITICAL, HIGH }
