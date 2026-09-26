package com.payflow.shared.domain;

/** The aggregate's current lifecycle state does not permit the requested transition. */
public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(String code, String message) {
        super(code, message);
    }
}
