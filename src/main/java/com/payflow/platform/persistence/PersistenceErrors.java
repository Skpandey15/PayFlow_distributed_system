package com.payflow.platform.persistence;

import java.sql.SQLException;
import java.util.Locale;

/** Classifies low-level persistence failures so adapters can translate them into port-level exceptions. */
public final class PersistenceErrors {

    private static final String UNIQUE_VIOLATION = "23505";

    private PersistenceErrors() {
    }

    /**
     * True if {@code error} was caused by a PostgreSQL unique violation on the named constraint.
     * Matching on the constraint name, and not just on SQLSTATE 23505, keeps an unrelated unique
     * violation from being misreported as, for example, an idempotency conflict.
     */
    public static boolean isUniqueViolation(Throwable error, String constraintName) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                String message = String.valueOf(sql.getMessage()).toLowerCase(Locale.ROOT);
                return message.contains(constraintName.toLowerCase(Locale.ROOT));
            }
        }
        return false;
    }
}
