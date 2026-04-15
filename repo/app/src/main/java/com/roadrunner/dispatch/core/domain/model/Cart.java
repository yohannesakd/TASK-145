package com.roadrunner.dispatch.core.domain.model;

public class Cart {
    public final String id;
    public final String orgId;
    public final String customerId;
    public final String storeId;
    public final String createdBy;

    public Cart(String id, String orgId, String customerId, String storeId, String createdBy) {
        this.id = id;
        this.orgId = orgId;
        this.customerId = customerId;
        this.storeId = storeId;
        this.createdBy = createdBy;
    }
}
