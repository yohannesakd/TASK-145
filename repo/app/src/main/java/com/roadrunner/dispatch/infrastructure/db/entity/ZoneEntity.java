package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "zones",
    indices = {
        @Index(value = {"org_id", "name"}, unique = true)
    }
)
public class ZoneEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "org_id")
    public String orgId;

    @NonNull
    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "score")
    public int score;

    @Nullable
    @ColumnInfo(name = "description")
    public String description;

    public ZoneEntity() {}

    public ZoneEntity(
            @NonNull String id,
            @NonNull String orgId,
            @NonNull String name,
            int score,
            @Nullable String description) {
        this.id = id;
        this.orgId = orgId;
        this.name = name;
        this.score = score;
        this.description = description;
    }
}
