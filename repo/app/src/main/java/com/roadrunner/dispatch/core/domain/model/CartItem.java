package com.roadrunner.dispatch.core.domain.model;

public class CartItem {
    public final String id;
    public final String cartId;
    public final String productId;
    public final int quantity;
    public final long unitPriceSnapshotCents;
    public final boolean conflictFlag;
    public final long originalPriceCents;

    public CartItem(String id, String cartId, String productId, int quantity,
                    long unitPriceSnapshotCents, boolean conflictFlag, long originalPriceCents) {
        this.id = id;
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPriceSnapshotCents = unitPriceSnapshotCents;
        this.conflictFlag = conflictFlag;
        this.originalPriceCents = originalPriceCents;
    }
}
