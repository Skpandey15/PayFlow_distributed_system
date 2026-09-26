package com.payflow.account.application.usecase;

/** Permission names (JWT scopes) understood by the Account context. */
public final class AccountPermissions {

    public static final String READ = "accounts:read";
    public static final String WRITE = "accounts:write";
    public static final String ADMIN = "accounts:admin";

    private AccountPermissions() {
    }
}
