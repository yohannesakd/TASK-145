package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "carts",
    indices = {
        @Index(value = {"org_id", "customer_id", "store_id"}, unique = true)
    }
)
public class CartEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "org_id")
    public String orgId;

    @NonNull
    @ColumnInfo(name = "customer_id")
    public String customerId;

    @NonNull
    @ColumnInfo(name = "store_id")
    public String storeId;

    /** userId of the creator */
    @NonNull
    @ColumnInfo(name = "created_by")
    public String createdBy;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public CartEntity() {}

    public CartEntity(
            @NonNull String id,
            @NonNull String orgId,
            @NonNull String customerId,
            @NonNull String storeId,
            @NonNull String createdBy,
            long createdAt,
            long updatedAt) {
        this.id = id;
        this.orgId = orgId;
        this.customerId = customerId;
        this.storeId = storeId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
