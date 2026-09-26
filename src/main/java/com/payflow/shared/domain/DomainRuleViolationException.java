package com.payflow.shared.domain;

/** A business invariant was violated by the requested operation (the request is well-formed but not allowed). */
public class DomainRuleViolationException extends DomainException {

    public DomainRuleViolationException(String code, String message) {
        super(code, message);
    }
}
