package com.payflow.shared.application;

/** The caller is authenticated but lacks the permission for this operation (HTTP 403). */
public class ForbiddenException extends ApplicationException {

    public ForbiddenException(String code, String message) {
        super(code, message);
    }

    public static void requirePermission(Actor actor, String permission) {
        if (!actor.hasPermission(permission)) {
            throw new ForbiddenException("MISSING_PERMISSION", "Operation requires permission " + permission);
        }
    }
}
