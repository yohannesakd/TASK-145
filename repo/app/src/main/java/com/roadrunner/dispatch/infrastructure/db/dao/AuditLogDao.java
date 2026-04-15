package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.roadrunner.dispatch.infrastructure.db.entity.AuditLogEntity;

import java.util.List;

@Dao
public interface AuditLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(AuditLogEntity log);

    @Query("SELECT * FROM audit_logs WHERE case_id = :caseId AND org_id = :orgId ORDER BY created_at DESC")
    LiveData<List<AuditLogEntity>> getLogsForCase(String caseId, String orgId);

    @Query("SELECT * FROM audit_logs WHERE org_id = :orgId ORDER BY created_at DESC")
    LiveData<List<AuditLogEntity>> getLogsForOrg(String orgId);
}
