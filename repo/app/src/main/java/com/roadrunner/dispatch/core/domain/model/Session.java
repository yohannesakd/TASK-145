package com.roadrunner.dispatch.core.domain.model;

public class Session {
    public final String userId;
    public final String orgId;
    public final String role;
    public final long createdAt;

    public Session(String userId, String orgId, String role, long createdAt) {
        this.userId = userId;
        this.orgId = orgId;
        this.role = role;
        this.createdAt = createdAt;
    }
}
