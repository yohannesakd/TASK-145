package com.roadrunner.dispatch.core.domain.model;

public class User {
    public final String id;
    public final String orgId;
    public final String username;
    public final String role;
    public final boolean isActive;

    public User(String id, String orgId, String username, String role, boolean isActive) {
        this.id = id;
        this.orgId = orgId;
        this.username = username;
        this.role = role;
        this.isActive = isActive;
    }
}
