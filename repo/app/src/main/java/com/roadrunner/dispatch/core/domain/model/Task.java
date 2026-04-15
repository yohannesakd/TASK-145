package com.roadrunner.dispatch.core.domain.model;

public class Task {
    public final String id;
    public final String orgId;
    public final String title;
    public final String description;
    public final String status;
    public final String mode;
    public final String priority;
    public final String zoneId;
    public final long windowStart;
    public final long windowEnd;
    public final String assignedWorkerId;
    public final String createdBy;

    public Task(String id, String orgId, String title, String description, String status,
                String mode, String priority, String zoneId, long windowStart, long windowEnd,
                String assignedWorkerId, String createdBy) {
        this.id = id;
        this.orgId = orgId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.mode = mode;
        this.priority = priority;
        this.zoneId = zoneId;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.assignedWorkerId = assignedWorkerId;
        this.createdBy = createdBy;
    }
}
