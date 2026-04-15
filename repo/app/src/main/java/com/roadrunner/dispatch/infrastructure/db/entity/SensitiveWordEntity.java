package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "sensitive_words",
    indices = {
        @Index(value = {"word"}, unique = true)
    }
)
public class SensitiveWordEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    /** Stored lowercased */
    @NonNull
    @ColumnInfo(name = "word")
    public String word;

    @ColumnInfo(name = "is_zero_tolerance")
    public boolean isZeroTolerance;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public SensitiveWordEntity() {}

    public SensitiveWordEntity(
            @NonNull String id,
            @NonNull String word,
            boolean isZeroTolerance,
            long createdAt) {
        this.id = id;
        this.word = word;
        this.isZeroTolerance = isZeroTolerance;
        this.createdAt = createdAt;
    }
}
