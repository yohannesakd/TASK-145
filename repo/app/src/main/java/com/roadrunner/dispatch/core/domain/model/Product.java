package com.roadrunner.dispatch.core.domain.model;

public class Product {
    public final String id;
    public final String orgId;
    public final String name;
    public final String brand;
    public final String series;
    public final String model;
    public final String description;
    public final long unitPriceCents;
    public final double taxRate;
    public final boolean regulated;
    public final String status;
    public final String imageUri;

    public Product(String id, String orgId, String name, String brand, String series, String model,
                   String description, long unitPriceCents, double taxRate, boolean regulated,
                   String status, String imageUri) {
        this.id = id;
        this.orgId = orgId;
        this.name = name;
        this.brand = brand;
        this.series = series;
        this.model = model;
        this.description = description;
        this.unitPriceCents = unitPriceCents;
        this.taxRate = taxRate;
        this.regulated = regulated;
        this.status = status;
        this.imageUri = imageUri;
    }
}
