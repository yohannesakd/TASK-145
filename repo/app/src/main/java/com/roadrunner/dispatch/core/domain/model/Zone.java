package com.roadrunner.dispatch.core.domain.model;

public class Zone {
    public final String id;
    public final String orgId;
    public final String name;
    public final int score;
    public final String description;

    public Zone(String id, String orgId, String name, int score, String description) {
        this.id = id;
        this.orgId = orgId;
        this.name = name;
        this.score = score;
        this.description = description;
    }
}
