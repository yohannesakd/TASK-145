package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "tasks",
    indices = {
        @Index(value = {"org_id", "status", "updated_at"})
    }
)
public class TaskEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "org_id")
    public String orgId;

    @NonNull
    @ColumnInfo(name = "title")
    public String title;

    @NonNull
    @ColumnInfo(name = "description")
    public String description;

    /** OPEN | ASSIGNED | IN_PROGRESS | COMPLETED | CANCELLED */
    @NonNull
    @ColumnInfo(name = "status")
    public String status;

    /** GRAB_ORDER | ASSIGNED */
    @NonNull
    @ColumnInfo(name = "mode")
    public String mode;

    @ColumnInfo(name = "priority")
    public int priority;

    @NonNull
    @ColumnInfo(name = "zone_id")
    public String zoneId;

    @ColumnInfo(name = "window_start")
    public long windowStart;

    @ColumnInfo(name = "window_end")
    public long windowEnd;

    @Nullable
    @ColumnInfo(name = "assigned_worker_id")
    public String assignedWorkerId;

    @NonNull
    @ColumnInfo(name = "created_by")
    public String createdBy;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public TaskEntity() {}

    public TaskEntity(
            @NonNull String id,
            @NonNull String orgId,
            @NonNull String title,
            @NonNull String description,
            @NonNull String status,
            @NonNull String mode,
            int priority,
            @NonNull String zoneId,
            long windowStart,
            long windowEnd,
            @Nullable String assignedWorkerId,
            @NonNull String createdBy,
            long createdAt,
            long updatedAt) {
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
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
