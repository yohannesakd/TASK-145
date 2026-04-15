package com.roadrunner.dispatch.presentation.common;

import com.roadrunner.dispatch.di.ServiceLocator;

/**
 * Central role-check utility for fragment-level authorization.
 * Fragments call {@link #hasRole(String...)} to verify the current session role
 * matches one of the allowed roles before rendering protected content.
 */
public final class RoleGuard {

    private RoleGuard() {}

    /**
     * Returns true if the current session role matches any of the allowed roles.
     */
    public static boolean hasRole(String... allowedRoles) {
        String currentRole = ServiceLocator.getInstance().getSessionManager().getRole();
        return matchesRole(currentRole, allowedRoles);
    }

    /**
     * Returns true if {@code currentRole} matches any of the {@code allowedRoles}.
     * Extracted so unit tests can invoke the matching logic directly without
     * requiring a live SessionManager or ServiceLocator.
     */
    public static boolean matchesRole(String currentRole, String... allowedRoles) {
        if (currentRole == null || currentRole.isEmpty()) return false;
        for (String role : allowedRoles) {
            if (currentRole.equals(role)) return true;
        }
        return false;
    }

    /**
     * Returns the current session role, or empty string if not set.
     */
    public static String currentRole() {
        String role = ServiceLocator.getInstance().getSessionManager().getRole();
        return role != null ? role : "";
    }
}
