package com.payflow.shared.application;

/**
 * A dependency required to complete the request is unavailable (HTTP 503). Use cases raise it only when
 * nothing was committed, or when what was committed is resumable, so the caller may safely retry.
 */
public class DependencyUnavailableException extends ApplicationException {

    public DependencyUnavailableException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
