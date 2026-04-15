package com.roadrunner.dispatch.core.domain.model;

public class Order {
    public final String id;
    public final String orgId;
    public final String cartId;
    public final String customerId;
    public final String storeId;
    public final String status;
    public final long subtotalCents;
    public final long discountCents;
    public final long taxCents;
    public final long shippingCents;
    public final long totalCents;
    public final String shippingTemplateId;
    public final String orderNotes;
    public final long totalsComputedAt;
    public final boolean totalsStale;

    public Order(String id, String orgId, String cartId, String customerId, String storeId,
                 String status, long subtotalCents, long discountCents, long taxCents,
                 long shippingCents, long totalCents, String shippingTemplateId,
                 String orderNotes, long totalsComputedAt, boolean totalsStale) {
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
    }
}
