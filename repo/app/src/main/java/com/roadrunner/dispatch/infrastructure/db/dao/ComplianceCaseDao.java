package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.roadrunner.dispatch.infrastructure.db.entity.ComplianceCaseEntity;

import java.util.List;

@Dao
public interface ComplianceCaseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(ComplianceCaseEntity complianceCase);

    @Update
    void update(ComplianceCaseEntity complianceCase);

    @Query("SELECT * FROM compliance_cases WHERE id = :id AND org_id = :orgId")
    ComplianceCaseEntity findByIdAndOrg(String id, String orgId);

    @Query("SELECT * FROM compliance_cases WHERE org_id = :orgId ORDER BY updated_at DESC")
    LiveData<List<ComplianceCaseEntity>> getCases(String orgId);

    @Query("SELECT * FROM compliance_cases WHERE org_id = :orgId AND status = :status ORDER BY updated_at DESC")
    LiveData<List<ComplianceCaseEntity>> getCasesByStatus(String orgId, String status);

    @Query("SELECT * FROM compliance_cases WHERE employer_id = :employerId AND org_id = :orgId ORDER BY updated_at DESC")
    List<ComplianceCaseEntity> getCasesForEmployer(String employerId, String orgId);
}
