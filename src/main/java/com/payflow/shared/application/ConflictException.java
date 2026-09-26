package com.payflow.shared.application;

/** The request conflicts with the current state of the resource (HTTP 409). */
public class ConflictException extends ApplicationException {

    public ConflictException(String code, String message) {
        super(code, message);
    }

    public ConflictException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
