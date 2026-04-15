package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "reputation_events",
    indices = {
        @Index(value = {"worker_id", "created_at"})
    }
)
public class ReputationEventEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "worker_id")
    public String workerId;

    /** TASK_COMPLETED | TASK_FAILED | RATING_RECEIVED | PENALTY */
    @NonNull
    @ColumnInfo(name = "event_type")
    public String eventType;

    @ColumnInfo(name = "delta")
    public double delta;

    @Nullable
    @ColumnInfo(name = "task_id")
    public String taskId;

    @Nullable
    @ColumnInfo(name = "notes")
    public String notes;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public ReputationEventEntity() {}

    public ReputationEventEntity(
            @NonNull String id,
            @NonNull String workerId,
            @NonNull String eventType,
            double delta,
            @Nullable String taskId,
            @Nullable String notes,
            long createdAt) {
        this.id = id;
        this.workerId = workerId;
        this.eventType = eventType;
        this.delta = delta;
        this.taskId = taskId;
        this.notes = notes;
        this.createdAt = createdAt;
    }
}
