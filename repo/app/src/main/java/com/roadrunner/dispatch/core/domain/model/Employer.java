package com.roadrunner.dispatch.core.domain.model;

public class Employer {
    public final String id;
    public final String orgId;
    public final String legalName;
    public final String ein;
    public final String streetAddress;
    public final String city;
    public final String state;
    public final String zipCode;
    public final String status;
    public final int warningCount;
    public final long suspendedUntil;
    public final boolean throttled;

    public Employer(String id, String orgId, String legalName, String ein, String streetAddress,
                    String city, String state, String zipCode, String status,
                    int warningCount, long suspendedUntil, boolean throttled) {
        this.id = id;
        this.orgId = orgId;
        this.legalName = legalName;
        this.ein = ein;
        this.streetAddress = streetAddress;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.status = status;
        this.warningCount = warningCount;
        this.suspendedUntil = suspendedUntil;
        this.throttled = throttled;
    }
}
