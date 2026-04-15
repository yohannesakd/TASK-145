package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.roadrunner.dispatch.infrastructure.db.entity.WorkerEntity;

import java.util.List;

@Dao
public interface WorkerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(WorkerEntity worker);

    @Update
    void update(WorkerEntity worker);

    @Query("SELECT * FROM workers WHERE id = :id AND org_id = :orgId")
    WorkerEntity findByIdAndOrg(String id, String orgId);

    @Nullable
    @Query("SELECT * FROM workers WHERE user_id = :userId AND org_id = :orgId")
    WorkerEntity findByUserIdAndOrg(String userId, String orgId);

    @Query("SELECT * FROM workers WHERE org_id = :orgId ORDER BY name ASC")
    LiveData<List<WorkerEntity>> getWorkers(String orgId);

    @Query("SELECT * FROM workers WHERE org_id = :orgId AND status = :status")
    List<WorkerEntity> getWorkersByStatus(String orgId, String status);

    @Query("UPDATE workers SET current_workload = current_workload + :delta WHERE id = :id AND org_id = :orgId")
    void adjustWorkload(String id, int delta, String orgId);

    @Query("UPDATE workers SET reputation_score = :score WHERE id = :id AND org_id = :orgId")
    void updateReputationScore(String id, double score, String orgId);
}
