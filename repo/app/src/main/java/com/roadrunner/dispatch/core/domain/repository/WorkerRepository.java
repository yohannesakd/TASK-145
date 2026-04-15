package com.roadrunner.dispatch.core.domain.repository;

import androidx.lifecycle.LiveData;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import java.util.List;

public interface WorkerRepository {
    LiveData<List<Worker>> getWorkers(String orgId);
    List<Worker> getWorkersByStatus(String orgId, String status);
    Worker getByIdScoped(String id, String orgId);
    Worker getByUserIdScoped(String userId, String orgId);
    void insert(Worker worker);
    void update(Worker worker);
    void adjustWorkload(String workerId, int delta, String orgId);
    void updateReputationScore(String workerId, double score, String orgId);
    void addReputationEvent(ReputationEvent event);
    double getAverageReputation(String workerId);
    LiveData<List<ReputationEvent>> getReputationEvents(String workerId);
}
