package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.roadrunner.dispatch.infrastructure.db.entity.ReputationEventEntity;

import java.util.List;

@Dao
public interface ReputationEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(ReputationEventEntity event);

    @Query("SELECT * FROM reputation_events WHERE worker_id = :workerId ORDER BY created_at DESC")
    LiveData<List<ReputationEventEntity>> getEventsForWorker(String workerId);

    @Query("SELECT AVG(delta) FROM reputation_events WHERE worker_id = :workerId")
    double getAverageScore(String workerId);

    @Query("SELECT * FROM reputation_events WHERE worker_id = :workerId ORDER BY created_at DESC LIMIT :limit")
    List<ReputationEventEntity> getRecentEvents(String workerId, int limit);
}
