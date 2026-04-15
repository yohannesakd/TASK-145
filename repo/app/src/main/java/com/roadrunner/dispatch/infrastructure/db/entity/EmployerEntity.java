package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "employers",
    indices = {
        @Index(value = {"org_id", "status"}),
        @Index(value = {"org_id", "ein"}, unique = true)
    }
)
public class EmployerEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "org_id")
    public String orgId;

    @NonNull
    @ColumnInfo(name = "legal_name")
    public String legalName;

    @NonNull
    @ColumnInfo(name = "ein")
    public String ein;

    @NonNull
    @ColumnInfo(name = "street_address")
    public String streetAddress;

    @NonNull
    @ColumnInfo(name = "city")
    public String city;

    /** 2-letter state code */
    @NonNull
    @ColumnInfo(name = "state")
    public String state;

    @NonNull
    @ColumnInfo(name = "zip_code")
    public String zipCode;

    /** PENDING | VERIFIED | SUSPENDED | DEACTIVATED */
    @NonNull
    @ColumnInfo(name = "status")
    public String status;

    @ColumnInfo(name = "warning_count")
    public int warningCount;

    /** 0 if not suspended */
    @ColumnInfo(name = "suspended_until")
    public long suspendedUntil;

    @ColumnInfo(name = "throttled")
    public boolean throttled;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public EmployerEntity() {}

    public EmployerEntity(
            @NonNull String id,
            @NonNull String orgId,
            @NonNull String legalName,
            @NonNull String ein,
            @NonNull String streetAddress,
            @NonNull String city,
            @NonNull String state,
            @NonNull String zipCode,
            @NonNull String status,
            int warningCount,
            long suspendedUntil,
            boolean throttled,
            long createdAt,
            long updatedAt) {
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
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
