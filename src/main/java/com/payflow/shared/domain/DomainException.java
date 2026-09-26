package com.payflow.shared.domain;

/**
 * Base type for violations of business rules detected inside the domain model.
 * Carries a stable, machine-readable {@code code} that inbound adapters translate into API errors.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
