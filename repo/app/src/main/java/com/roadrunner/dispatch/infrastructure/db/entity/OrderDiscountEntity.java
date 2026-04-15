package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

@Entity(
    tableName = "order_discounts",
    primaryKeys = {"order_id", "discount_rule_id"},
    foreignKeys = {
        @ForeignKey(
            entity = OrderEntity.class,
            parentColumns = "id",
            childColumns = "order_id",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = DiscountRuleEntity.class,
            parentColumns = "id",
            childColumns = "discount_rule_id"
        )
    }
)
public class OrderDiscountEntity {

    @NonNull
    @ColumnInfo(name = "order_id")
    public String orderId;

    @NonNull
    @ColumnInfo(name = "discount_rule_id")
    public String discountRuleId;

    @ColumnInfo(name = "applied_amount_cents")
    public long appliedAmountCents;

    @ColumnInfo(name = "applied_at")
    public long appliedAt;

    public OrderDiscountEntity() {}

    public OrderDiscountEntity(
            @NonNull String orderId,
            @NonNull String discountRuleId,
            long appliedAmountCents,
            long appliedAt) {
        this.orderId = orderId;
        this.discountRuleId = discountRuleId;
        this.appliedAmountCents = appliedAmountCents;
        this.appliedAt = appliedAt;
    }
}
