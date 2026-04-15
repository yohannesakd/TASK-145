package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "audit_logs",
    indices = {
        @Index(value = {"target_type", "target_id"}),
        @Index(value = {"created_at"}),
        @Index(value = {"case_id"})
    }
)
public class AuditLogEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "org_id")
    public String orgId;

    @NonNull
    @ColumnInfo(name = "actor_id")
    public String actorId;

    /**
     * WARNING_ISSUED | SUSPENSION_APPLIED | TAKEDOWN | THROTTLE_APPLIED |
     * THROTTLE_REMOVED | CASE_OPENED | CASE_RESOLVED | etc.
     */
    @NonNull
    @ColumnInfo(name = "action")
    public String action;

    /** EMPLOYER | ORDER | TASK | USER | PRODUCT */
    @NonNull
    @ColumnInfo(name = "target_type")
    public String targetType;

    @NonNull
    @ColumnInfo(name = "target_id")
    public String targetId;

    /** JSON details blob */
    @NonNull
    @ColumnInfo(name = "details")
    public String details;

    @Nullable
    @ColumnInfo(name = "case_id")
    public String caseId;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public AuditLogEntity() {}

    public AuditLogEntity(
            @NonNull String id,
            @NonNull String orgId,
            @NonNull String actorId,
            @NonNull String action,
            @NonNull String targetType,
            @NonNull String targetId,
            @NonNull String details,
            @Nullable String caseId,
            long createdAt) {
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
