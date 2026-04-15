package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "products",
    indices = {
        @Index(value = {"org_id", "status", "updated_at"}),
        @Index(value = {"brand", "series", "model"})
    }
)
public class ProductEntity {

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
    @ColumnInfo(name = "brand")
    public String brand;

    @NonNull
    @ColumnInfo(name = "series")
    public String series;

    @NonNull
    @ColumnInfo(name = "model")
    public String model;

    @NonNull
    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "unit_price_cents")
    public long unitPriceCents;

    @ColumnInfo(name = "tax_rate")
    public double taxRate;

    @ColumnInfo(name = "regulated")
    public boolean regulated;

    /** ACTIVE | INACTIVE | FLAGGED */
    @NonNull
    @ColumnInfo(name = "status")
    public String status;

    @Nullable
    @ColumnInfo(name = "image_uri")
    public String imageUri;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public ProductEntity() {}

    public ProductEntity(
            @NonNull String id,
            @NonNull String orgId,
            @NonNull String name,
            @NonNull String brand,
            @NonNull String series,
            @NonNull String model,
            @NonNull String description,
            long unitPriceCents,
            double taxRate,
            boolean regulated,
            @NonNull String status,
            @Nullable String imageUri,
            long createdAt,
            long updatedAt) {
        this.id = id;
        this.orgId = orgId;
        this.name = name;
        this.brand = brand;
        this.series = series;
        this.model = model;
        this.description = description;
        this.unitPriceCents = unitPriceCents;
        this.taxRate = taxRate;
        this.regulated = regulated;
        this.status = status;
        this.imageUri = imageUri;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
