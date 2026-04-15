package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "orders",
    indices = {
        @Index(value = {"org_id", "status", "updated_at"})
    }
)
public class OrderEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "org_id")
    public String orgId;

    @NonNull
    @ColumnInfo(name = "cart_id")
    public String cartId;

    @NonNull
    @ColumnInfo(name = "customer_id")
    public String customerId;

    @NonNull
    @ColumnInfo(name = "store_id")
    public String storeId;

    /** DRAFT | PENDING_REVIEW | FINALIZED | CANCELLED */
    @NonNull
    @ColumnInfo(name = "status")
    public String status;

    @ColumnInfo(name = "subtotal_cents")
    public long subtotalCents;

    @ColumnInfo(name = "discount_cents")
    public long discountCents;

    @ColumnInfo(name = "tax_cents")
    public long taxCents;

    @ColumnInfo(name = "shipping_cents")
    public long shippingCents;

    @ColumnInfo(name = "total_cents")
    public long totalCents;

    @Nullable
    @ColumnInfo(name = "shipping_template_id")
    public String shippingTemplateId;

    @Nullable
    @ColumnInfo(name = "order_notes")
    public String orderNotes;

    /** 0 if totals not yet computed */
    @ColumnInfo(name = "totals_computed_at")
    public long totalsComputedAt;

    @ColumnInfo(name = "totals_stale")
    public boolean totalsStale;

    @NonNull
    @ColumnInfo(name = "created_by")
    public String createdBy;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public OrderEntity() {}

    public OrderEntity(
            @NonNull String id,
            @NonNull String orgId,
            @NonNull String cartId,
            @NonNull String customerId,
            @NonNull String storeId,
            @NonNull String status,
            long subtotalCents,
            long discountCents,
            long taxCents,
            long shippingCents,
            long totalCents,
            @Nullable String shippingTemplateId,
            @Nullable String orderNotes,
            long totalsComputedAt,
            boolean totalsStale,
            @NonNull String createdBy,
            long createdAt,
            long updatedAt) {
        this.id = id;
        this.orgId = orgId;
        this.cartId = cartId;
        this.customerId = customerId;
        this.storeId = storeId;
        this.status = status;
        this.subtotalCents = subtotalCents;
        this.discountCents = discountCents;
        this.taxCents = taxCents;
        this.shippingCents = shippingCents;
        this.totalCents = totalCents;
        this.shippingTemplateId = shippingTemplateId;
        this.orderNotes = orderNotes;
        this.totalsComputedAt = totalsComputedAt;
        this.totalsStale = totalsStale;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
