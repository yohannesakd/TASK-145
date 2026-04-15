package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "order_items",
    foreignKeys = {
        @ForeignKey(
            entity = OrderEntity.class,
            parentColumns = "id",
            childColumns = "order_id",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index(value = {"order_id", "product_id"})
    }
)
public class OrderItemEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "order_id")
    public String orderId;

    @NonNull
    @ColumnInfo(name = "product_id")
    public String productId;

    /** Snapshot of the product name at time of order */
    @NonNull
    @ColumnInfo(name = "product_name")
    public String productName;

    @ColumnInfo(name = "quantity")
    public int quantity;

    @ColumnInfo(name = "unit_price_cents")
    public long unitPriceCents;

    @ColumnInfo(name = "line_total_cents")
    public long lineTotalCents;

    @ColumnInfo(name = "tax_rate")
    public double taxRate;

    @ColumnInfo(name = "regulated")
    public boolean regulated;

    public OrderItemEntity() {}

    public OrderItemEntity(
            @NonNull String id,
            @NonNull String orderId,
            @NonNull String productId,
            @NonNull String productName,
            int quantity,
            long unitPriceCents,
            long lineTotalCents,
            double taxRate,
            boolean regulated) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
        this.lineTotalCents = lineTotalCents;
        this.taxRate = taxRate;
        this.regulated = regulated;
    }
}
