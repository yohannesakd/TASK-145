package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "discount_rules",
    indices = {
        @Index(value = {"org_id", "status"})
    }
)
public class DiscountRuleEntity {

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

    /** PERCENT_OFF | FLAT_OFF */
    @NonNull
    @ColumnInfo(name = "type")
    public String type;

    @ColumnInfo(name = "value")
    public double value;

    /** ACTIVE | EXPIRED | DISABLED */
    @NonNull
    @ColumnInfo(name = "status")
    public String status;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public DiscountRuleEntity() {}

    public DiscountRuleEntity(
            @NonNull String id,
            @NonNull String orgId,
            @NonNull String name,
            @NonNull String type,
            double value,
            @NonNull String status,
            long createdAt) {
        this.id = id;
        this.orgId = orgId;
        this.name = name;
        this.type = type;
        this.value = value;
        this.status = status;
        this.createdAt = createdAt;
    }
}
