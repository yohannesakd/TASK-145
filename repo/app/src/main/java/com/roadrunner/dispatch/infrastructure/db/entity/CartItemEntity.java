package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "cart_items",
    foreignKeys = {
        @ForeignKey(
            entity = CartEntity.class,
            parentColumns = "id",
            childColumns = "cart_id",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = ProductEntity.class,
            parentColumns = "id",
            childColumns = "product_id"
        )
    },
    indices = {
        @Index(value = {"cart_id", "product_id"}, unique = true),
        @Index(value = {"cart_id"})
    }
)
public class CartItemEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "cart_id")
    public String cartId;

    @NonNull
    @ColumnInfo(name = "product_id")
    public String productId;

    @ColumnInfo(name = "quantity")
    public int quantity;

    @ColumnInfo(name = "unit_price_snapshot_cents")
    public long unitPriceSnapshotCents;

    @ColumnInfo(name = "conflict_flag")
    public boolean conflictFlag;

    /** 0 if no conflict */
    @ColumnInfo(name = "original_price_cents")
    public long originalPriceCents;

    @ColumnInfo(name = "added_at")
    public long addedAt;

    public CartItemEntity() {}

    public CartItemEntity(
            @NonNull String id,
            @NonNull String cartId,
            @NonNull String productId,
            int quantity,
            long unitPriceSnapshotCents,
            boolean conflictFlag,
            long originalPriceCents,
            long addedAt) {
        this.id = id;
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPriceSnapshotCents = unitPriceSnapshotCents;
        this.conflictFlag = conflictFlag;
        this.originalPriceCents = originalPriceCents;
        this.addedAt = addedAt;
    }
}
