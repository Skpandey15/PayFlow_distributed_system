package com.payflow.shared.application;

/** The request is well-formed but semantically invalid for this operation (HTTP 422). */
public class UnprocessableException extends ApplicationException {

    public UnprocessableException(String code, String message) {
        super(code, message);
    }
}
