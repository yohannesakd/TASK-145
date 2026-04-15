package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "workers",
    indices = {
        @Index(value = {"org_id", "status"}),
        @Index(value = {"user_id"}, unique = true)
    }
)
public class WorkerEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "user_id")
    public String userId;

    @NonNull
    @ColumnInfo(name = "org_id")
    public String orgId;

    @NonNull
    @ColumnInfo(name = "name")
    public String name;

    /** AVAILABLE | BUSY | OFFLINE */
    @NonNull
    @ColumnInfo(name = "status")
    public String status;

    @ColumnInfo(name = "current_workload")
    public int currentWorkload;

    /** Default 3.0 */
    @ColumnInfo(name = "reputation_score")
    public double reputationScore;

    @Nullable
    @ColumnInfo(name = "zone_id")
    public String zoneId;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public WorkerEntity() {}

    public WorkerEntity(
            @NonNull String id,
            @NonNull String userId,
            @NonNull String orgId,
            @NonNull String name,
            @NonNull String status,
            int currentWorkload,
            double reputationScore,
            @Nullable String zoneId,
            long createdAt,
            long updatedAt) {
        this.id = id;
        this.userId = userId;
        this.orgId = orgId;
        this.name = name;
        this.status = status;
        this.currentWorkload = currentWorkload;
        this.reputationScore = reputationScore;
        this.zoneId = zoneId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
