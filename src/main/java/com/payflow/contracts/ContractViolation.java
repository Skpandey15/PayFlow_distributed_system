package com.payflow.contracts;

/** A message does not satisfy its published contract (missing or invalid mandatory field). Never retryable. */
public class ContractViolation extends RuntimeException {

    public ContractViolation(String message) {
        super(message);
    }

    public static <T> T required(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new ContractViolation("Missing mandatory field: " + field);
        }
        return value;
    }
}
