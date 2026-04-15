package com.roadrunner.dispatch.infrastructure.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "users",
    indices = {
        @Index(value = {"username"}, unique = true),
        @Index(value = {"org_id", "role"})
    }
)
public class UserEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "org_id")
    public String orgId;

    @NonNull
    @ColumnInfo(name = "username")
    public String username;

    @NonNull
    @ColumnInfo(name = "password_hash")
    public String passwordHash;

    @NonNull
    @ColumnInfo(name = "password_salt")
    public String passwordSalt;

    /** ADMIN | DISPATCHER | WORKER | COMPLIANCE_REVIEWER */
    @NonNull
    @ColumnInfo(name = "role")
    public String role;

    @ColumnInfo(name = "is_active")
    public boolean isActive;

    @ColumnInfo(name = "failed_attempts")
    public int failedAttempts;

    /** 0 means not locked */
    @ColumnInfo(name = "locked_until")
    public long lockedUntil;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public UserEntity() {}

    public UserEntity(
            @NonNull String id,
            @NonNull String orgId,
            @NonNull String username,
            @NonNull String passwordHash,
            @NonNull String passwordSalt,
            @NonNull String role,
            boolean isActive,
            int failedAttempts,
            long lockedUntil,
            long createdAt,
            long updatedAt) {
        this.id = id;
        this.orgId = orgId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.role = role;
        this.isActive = isActive;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
