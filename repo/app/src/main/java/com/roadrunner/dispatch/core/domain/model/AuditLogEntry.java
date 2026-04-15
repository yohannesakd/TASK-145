package com.roadrunner.dispatch.core.domain.model;

public class AuditLogEntry {
    public final String id;
    public final String orgId;
    public final String actorId;
    public final String action;
    public final String targetType;
    public final String targetId;
    public final String details;
    public final String caseId;
    public final long createdAt;

    public AuditLogEntry(String id, String orgId, String actorId, String action,
                         String targetType, String targetId, String details,
                         String caseId, long createdAt) {
        this.id = id;
        this.orgId = orgId;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
        this.caseId = caseId;
        this.createdAt = createdAt;
    }
}
