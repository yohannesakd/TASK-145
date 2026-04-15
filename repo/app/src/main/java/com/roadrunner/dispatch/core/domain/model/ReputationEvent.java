package com.roadrunner.dispatch.core.domain.model;

public class ReputationEvent {
    public final String id;
    public final String workerId;
    public final String eventType;
    public final double delta;
    public final String taskId;
    public final String notes;

    public ReputationEvent(String id, String workerId, String eventType, double delta,
                           String taskId, String notes) {
        this.id = id;
        this.workerId = workerId;
        this.eventType = eventType;
        this.delta = delta;
        this.taskId = taskId;
        this.notes = notes;
    }
}
