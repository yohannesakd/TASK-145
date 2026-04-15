package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "reports",
    indices = {
        @Index(value = {"case_id", "created_at"})
    }
)
public class ReportEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "org_id")
    public String orgId;

    @Nullable
    @ColumnInfo(name = "case_id")
    public String caseId;

    @NonNull
    @ColumnInfo(name = "reported_by")
    public String reportedBy;

    @NonNull
    @ColumnInfo(name = "target_type")
    public String targetType;

    @NonNull
    @ColumnInfo(name = "target_id")
    public String targetId;

    @NonNull
    @ColumnInfo(name = "description")
    public String description;

    @Nullable
    @ColumnInfo(name = "evidence_uri")
    public String evidenceUri;

    /** SHA-256 hash of evidence, nullable */
    @Nullable
    @ColumnInfo(name = "evidence_hash")
    public String evidenceHash;

    /** FILED | REVIEWED | DISMISSED */
    @NonNull
    @ColumnInfo(name = "status")
    public String status;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public ReportEntity() {}

    public ReportEntity(
            @NonNull String id,
            @NonNull String orgId,
            @Nullable String caseId,
            @NonNull String reportedBy,
            @NonNull String targetType,
            @NonNull String targetId,
            @NonNull String description,
            @Nullable String evidenceUri,
            @Nullable String evidenceHash,
            @NonNull String status,
            long createdAt) {
        this.id = id;
        this.orgId = orgId;
        this.caseId = caseId;
        this.reportedBy = reportedBy;
        this.targetType = targetType;
        this.targetId = targetId;
        this.description = description;
        this.evidenceUri = evidenceUri;
        this.evidenceHash = evidenceHash;
        this.status = status;
        this.createdAt = createdAt;
    }
}
