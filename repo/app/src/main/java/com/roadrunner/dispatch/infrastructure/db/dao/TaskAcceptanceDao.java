package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.roadrunner.dispatch.infrastructure.db.entity.TaskAcceptanceEntity;

import java.util.List;

@Dao
public interface TaskAcceptanceDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(TaskAcceptanceEntity acceptance);

    @Query("SELECT * FROM task_acceptances WHERE task_id = :taskId")
    List<TaskAcceptanceEntity> getAcceptancesForTask(String taskId);

    @Nullable
    @Query("SELECT * FROM task_acceptances WHERE task_id = :taskId AND accepted_by = :workerId")
    TaskAcceptanceEntity findByTaskAndWorker(String taskId, String workerId);
}
