package com.roadrunner.dispatch.core.domain.model;

public class DiscountRule {
    public final String id;
    public final String orgId;
    public final String name;
    public final String type;
    public final double value;
    public final String status;

    public DiscountRule(String id, String orgId, String name, String type, double value, String status) {
        this.id = id;
        this.orgId = orgId;
        this.name = name;
        this.type = type;
        this.value = value;
        this.status = status;
    }
}
