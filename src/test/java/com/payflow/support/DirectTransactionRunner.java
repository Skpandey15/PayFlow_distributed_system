package com.payflow.support;

import com.payflow.shared.application.TransactionRunner;

import java.util.function.Supplier;

/** Unit-test runner: executes the work inline and counts transaction boundaries so tests can assert them. */
public class DirectTransactionRunner implements TransactionRunner {

    private int readWriteTransactions;
    private int readOnlyTransactions;
    private boolean active;

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        readWriteTransactions++;
        return run(work);
    }

    @Override
    public <T> T readOnly(Supplier<T> work) {
        readOnlyTransactions++;
        return run(work);
    }

    private <T> T run(Supplier<T> work) {
        active = true;
        try {
            return work.get();
        } finally {
            active = false;
        }
    }

    /** True while a unit of work is executing, so tests can assert that remote calls happen outside transactions. */
    public boolean isActive() {
        return active;
    }

    public int readWriteTransactions() {
        return readWriteTransactions;
    }

    public int readOnlyTransactions() {
        return readOnlyTransactions;
    }
}
