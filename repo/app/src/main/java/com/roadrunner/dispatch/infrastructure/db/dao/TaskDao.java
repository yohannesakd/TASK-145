package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.roadrunner.dispatch.infrastructure.db.entity.TaskEntity;

import java.util.List;

@Dao
public interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(TaskEntity task);

    @Update
    void update(TaskEntity task);

    @Query("SELECT * FROM tasks WHERE id = :id AND org_id = :orgId")
    TaskEntity findByIdAndOrg(String id, String orgId);

    @Query("SELECT * FROM tasks WHERE org_id = :orgId ORDER BY updated_at DESC")
    LiveData<List<TaskEntity>> getTasks(String orgId);

    @Query("SELECT * FROM tasks WHERE org_id = :orgId AND status = :status ORDER BY updated_at DESC")
    LiveData<List<TaskEntity>> getTasksByStatus(String orgId, String status);

    @Query("SELECT * FROM tasks WHERE org_id = :orgId AND status = 'OPEN' AND mode = :mode AND window_end > :now ORDER BY priority DESC")
    List<TaskEntity> getOpenTasksByMode(String orgId, String mode, long now);

    @Query("UPDATE tasks SET status = 'ASSIGNED', assigned_worker_id = :workerId, updated_at = :updatedAt WHERE id = :taskId AND status = 'OPEN'")
    int claimIfOpen(String taskId, String workerId, long updatedAt);

    @Query("SELECT * FROM tasks WHERE org_id = :orgId AND assigned_worker_id = :workerId AND status IN ('ASSIGNED', 'IN_PROGRESS') ORDER BY updated_at DESC")
    List<TaskEntity> getWorkerActiveTasks(String orgId, String workerId);

    @Query("SELECT * FROM tasks WHERE org_id = :orgId AND assigned_worker_id = :workerId AND status IN ('ASSIGNED', 'IN_PROGRESS') ORDER BY updated_at DESC")
    LiveData<List<TaskEntity>> getWorkerActiveTasksLive(String orgId, String workerId);
}
