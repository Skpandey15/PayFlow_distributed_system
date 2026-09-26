package com.payflow.shared.application;

import java.util.Objects;
import java.util.Set;

/**
 * The verified caller on whose behalf a use case executes.
 *
 * <p>Deliberately framework-free. Inbound adapters build it from a validated JWT (subject + granted
 * scopes), so use cases can enforce <em>object-level</em> authorisation (ownership) without knowing how
 * the caller authenticated. Coarse-grained scope checks happen at the HTTP edge and ownership checks
 * happen here, next to the data, which gives defence in depth.
 */
public record Actor(String subject, Set<String> permissions) {

    public Actor {
        Objects.requireNonNull(subject, "subject");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        permissions = Set.copyOf(permissions);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean is(String otherSubject) {
        return subject.equals(otherSubject);
    }
}
