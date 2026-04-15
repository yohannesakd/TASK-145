package com.roadrunner.dispatch.core.domain.model;

public class OrderItem {
    public final String id;
    public final String orderId;
    public final String productId;
    public final String productName;
    public final int quantity;
    public final long unitPriceCents;
    public final long lineTotalCents;
    public final double taxRate;
    public final boolean regulated;

    public OrderItem(String id, String orderId, String productId, String productName,
                     int quantity, long unitPriceCents, long lineTotalCents,
                     double taxRate, boolean regulated) {
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
