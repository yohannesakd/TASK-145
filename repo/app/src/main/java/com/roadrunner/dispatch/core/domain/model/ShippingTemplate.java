package com.roadrunner.dispatch.core.domain.model;

public class ShippingTemplate {
    public final String id;
    public final String orgId;
    public final String name;
    public final String description;
    public final long costCents;
    public final int minDays;
    public final int maxDays;
    public final boolean isPickup;

    public ShippingTemplate(String id, String orgId, String name, String description,
                            long costCents, int minDays, int maxDays, boolean isPickup) {
        this.id = id;
        this.orgId = orgId;
        this.name = name;
        this.description = description;
        this.costCents = costCents;
        this.minDays = minDays;
        this.maxDays = maxDays;
        this.isPickup = isPickup;
    }
}
