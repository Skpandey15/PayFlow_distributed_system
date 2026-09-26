package com.payflow.shared.application;

/**
 * Base type for failures raised by use cases, as opposed to domain invariants. Subclasses express the
 * <em>category</em> of failure; inbound adapters map the category to a transport status.
 */
public abstract class ApplicationException extends RuntimeException {

    private final String code;

    protected ApplicationException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected ApplicationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
