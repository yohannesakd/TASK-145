package com.roadrunner.dispatch.core.domain.model;

public class UserAuthInfo {
    public final String userId;
    public final String orgId;
    public final String username;
    public final String role;
    public final boolean isActive;
    public final String passwordHash;
    public final String passwordSalt;
    public final int failedAttempts;
    public final long lockedUntil;

    public UserAuthInfo(String userId, String orgId, String username, String role, boolean isActive,
                        String passwordHash, String passwordSalt, int failedAttempts, long lockedUntil) {
        this.userId = userId;
        this.orgId = orgId;
        this.username = username;
        this.role = role;
        this.isActive = isActive;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
    }
}
