package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "compliance_cases",
    indices = {
        @Index(value = {"org_id", "status", "updated_at"})
    }
)
public class ComplianceCaseEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "org_id")
    public String orgId;

    @Nullable
    @ColumnInfo(name = "employer_id")
    public String employerId;

    /** CONTENT_VIOLATION | HARASSMENT | EMPLOYER_ISSUE | OTHER */
    @NonNull
    @ColumnInfo(name = "case_type")
    public String caseType;

    /** OPEN | UNDER_REVIEW | RESOLVED | ESCALATED */
    @NonNull
    @ColumnInfo(name = "status")
    public String status;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    @NonNull
    @ColumnInfo(name = "severity")
    public String severity;

    @NonNull
    @ColumnInfo(name = "description")
    public String description;

    @NonNull
    @ColumnInfo(name = "created_by")
    public String createdBy;

    @Nullable
    @ColumnInfo(name = "assigned_to")
    public String assignedTo;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public ComplianceCaseEntity() {}

    public ComplianceCaseEntity(
            @NonNull String id,
            @NonNull String orgId,
            @Nullable String employerId,
            @NonNull String caseType,
            @NonNull String status,
            @NonNull String severity,
            @NonNull String description,
            @NonNull String createdBy,
            @Nullable String assignedTo,
            long createdAt,
            long updatedAt) {
        this.id = id;
        this.orgId = orgId;
        this.employerId = employerId;
        this.caseType = caseType;
        this.status = status;
        this.severity = severity;
        this.description = description;
        this.createdBy = createdBy;
        this.assignedTo = assignedTo;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
