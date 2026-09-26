package com.payflow.shared.application;

/**
 * The resource does not exist <em>or is not visible to the caller</em>. Both cases deliberately look the
 * same so that object identifiers cannot be probed for existence (anti-enumeration / BOLA defence).
 */
public class NotFoundException extends ApplicationException {

    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
