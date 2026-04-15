package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "shipping_templates",
    indices = {
        @Index(value = {"org_id"})
    }
)
public class ShippingTemplateEntity {

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

    @NonNull
    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "cost_cents")
    public long costCents;

    @ColumnInfo(name = "min_days")
    public int minDays;

    @ColumnInfo(name = "max_days")
    public int maxDays;

    @ColumnInfo(name = "is_pickup")
    public boolean isPickup;

    public ShippingTemplateEntity() {}

    public ShippingTemplateEntity(
            @NonNull String id,
            @NonNull String orgId,
            @NonNull String name,
            @NonNull String description,
            long costCents,
            int minDays,
            int maxDays,
            boolean isPickup) {
        this.id = id;
        this.orgId = orgId;
        this.name = name;
        this.description = description;
        this.costCents = costCents;
        this.minDays = minDays;
        this.maxDays = maxDays;
        this.isPickup = isPickup;
    }
}
