package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.roadrunner.dispatch.infrastructure.db.entity.ReportEntity;

import java.util.List;

@Dao
public interface ReportDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(ReportEntity report);

    @Update
    void update(ReportEntity report);

    @Query("SELECT * FROM reports WHERE id = :id AND org_id = :orgId")
    ReportEntity findByIdAndOrg(String id, String orgId);

    @Query("SELECT * FROM reports WHERE case_id = :caseId AND org_id = :orgId ORDER BY created_at DESC")
    LiveData<List<ReportEntity>> getReportsForCase(String caseId, String orgId);

    @Query("SELECT * FROM reports WHERE org_id = :orgId ORDER BY created_at DESC")
    LiveData<List<ReportEntity>> getReportsForOrg(String orgId);
}
