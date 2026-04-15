package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.roadrunner.dispatch.infrastructure.db.entity.EmployerEntity;

import java.util.List;

@Dao
public interface EmployerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(EmployerEntity employer);

    @Update
    void update(EmployerEntity employer);

    @Query("SELECT * FROM employers WHERE id = :id AND org_id = :orgId")
    EmployerEntity findByIdAndOrg(String id, String orgId);

    @Nullable
    @Query("SELECT * FROM employers WHERE ein = :ein AND org_id = :orgId LIMIT 1")
    EmployerEntity findByEinAndOrg(String ein, String orgId);

    @Query("SELECT * FROM employers WHERE org_id = :orgId ORDER BY updated_at DESC")
    LiveData<List<EmployerEntity>> getEmployers(String orgId);

    @Query("SELECT * FROM employers WHERE org_id = :orgId AND status = :status ORDER BY updated_at DESC")
    LiveData<List<EmployerEntity>> getEmployersByStatus(String orgId, String status);

    @Query("SELECT * FROM employers WHERE org_id = :orgId AND (throttled = 0 OR :includeThrottled = 1) ORDER BY updated_at DESC")
    LiveData<List<EmployerEntity>> getEmployersFilterThrottled(String orgId, boolean includeThrottled);

    @Query("SELECT * FROM employers WHERE org_id = :orgId ORDER BY updated_at DESC")
    List<EmployerEntity> getEmployersSync(String orgId);
}
