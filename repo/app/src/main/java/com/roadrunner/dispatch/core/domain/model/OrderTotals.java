package com.roadrunner.dispatch.core.domain.model;

public class OrderTotals {
    public final long subtotalCents;
    public final long discountCents;
    public final long taxCents;
    public final long shippingCents;
    public final long totalCents;

    public OrderTotals(long subtotalCents, long discountCents, long taxCents, long shippingCents,
                       long totalCents) {
        this.subtotalCents = subtotalCents;
        this.discountCents = discountCents;
        this.taxCents = taxCents;
        this.shippingCents = shippingCents;
        this.totalCents = totalCents;
    }
}
