package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "task_acceptances",
    indices = {
        @Index(value = {"task_id", "accepted_by"}, unique = true)
    }
)
public class TaskAcceptanceEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "task_id")
    public String taskId;

    /** workerId of the accepting worker */
    @NonNull
    @ColumnInfo(name = "accepted_by")
    public String acceptedBy;

    @ColumnInfo(name = "accepted_at")
    public long acceptedAt;

    /** ACCEPTED | REJECTED | SUPERSEDED */
    @NonNull
    @ColumnInfo(name = "status")
    public String status;

    public TaskAcceptanceEntity() {}

    public TaskAcceptanceEntity(
            @NonNull String id,
            @NonNull String taskId,
            @NonNull String acceptedBy,
            long acceptedAt,
            @NonNull String status) {
        this.id = id;
        this.taskId = taskId;
        this.acceptedBy = acceptedBy;
        this.acceptedAt = acceptedAt;
        this.status = status;
    }
}
