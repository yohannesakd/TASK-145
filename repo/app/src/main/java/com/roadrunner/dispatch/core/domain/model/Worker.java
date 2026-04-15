package com.roadrunner.dispatch.core.domain.model;

public class Worker {
    public final String id;
    public final String userId;
    public final String orgId;
    public final String name;
    public final String status;
    public final int currentWorkload;
    public final double reputationScore;
    public final String zoneId;

    public Worker(String id, String userId, String orgId, String name, String status,
                  int currentWorkload, double reputationScore, String zoneId) {
        this.id = id;
        this.userId = userId;
        this.orgId = orgId;
        this.name = name;
        this.status = status;
        this.currentWorkload = currentWorkload;
        this.reputationScore = reputationScore;
        this.zoneId = zoneId;
    }
}
