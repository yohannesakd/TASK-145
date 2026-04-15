package com.roadrunner.dispatch.infrastructure.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.core.domain.repository.WorkerRepository;
import com.roadrunner.dispatch.infrastructure.db.dao.ReputationEventDao;
import com.roadrunner.dispatch.infrastructure.db.dao.WorkerDao;
import com.roadrunner.dispatch.infrastructure.db.entity.ReputationEventEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.WorkerEntity;

import java.util.ArrayList;
import java.util.List;

public class WorkerRepositoryImpl implements WorkerRepository {

    private final WorkerDao workerDao;
    private final ReputationEventDao reputationEventDao;

    public WorkerRepositoryImpl(WorkerDao workerDao, ReputationEventDao reputationEventDao) {
        this.workerDao = workerDao;
        this.reputationEventDao = reputationEventDao;
    }

    @Override
    public LiveData<List<Worker>> getWorkers(String orgId) {
        return Transformations.map(workerDao.getWorkers(orgId), this::mapWorkerList);
    }

    @Override
    public List<Worker> getWorkersByStatus(String orgId, String status) {
        List<WorkerEntity> entities = workerDao.getWorkersByStatus(orgId, status);
        return mapWorkerList(entities);
    }

    @Override
    public Worker getByIdScoped(String id, String orgId) {
        WorkerEntity entity = workerDao.findByIdAndOrg(id, orgId);
        return entity != null ? mapWorkerToDomain(entity) : null;
    }

    @Override
    public Worker getByUserIdScoped(String userId, String orgId) {
        WorkerEntity entity = workerDao.findByUserIdAndOrg(userId, orgId);
        return entity != null ? mapWorkerToDomain(entity) : null;
    }

    @Override
    public void insert(Worker worker) {
        workerDao.insert(mapWorkerToEntity(worker));
    }

    @Override
    public void update(Worker worker) {
        workerDao.update(mapWorkerToEntity(worker));
    }

    @Override
    public void adjustWorkload(String workerId, int delta, String orgId) {
        workerDao.adjustWorkload(workerId, delta, orgId);
    }

    @Override
    public void updateReputationScore(String workerId, double score, String orgId) {
        workerDao.updateReputationScore(workerId, score, orgId);
    }

    @Override
    public void addReputationEvent(ReputationEvent event) {
        reputationEventDao.insert(mapEventToEntity(event));
    }

    @Override
    public double getAverageReputation(String workerId) {
        return reputationEventDao.getAverageScore(workerId);
    }

    @Override
    public LiveData<List<ReputationEvent>> getReputationEvents(String workerId) {
        return Transformations.map(
                reputationEventDao.getEventsForWorker(workerId),
                this::mapEventList
        );
    }

    // --- Worker mapping ---

    private Worker mapWorkerToDomain(WorkerEntity e) {
        return new Worker(e.id, e.userId, e.orgId, e.name, e.status,
                e.currentWorkload, e.reputationScore, e.zoneId);
    }

    private WorkerEntity mapWorkerToEntity(Worker w) {
        long now = System.currentTimeMillis();
        return new WorkerEntity(w.id, w.userId, w.orgId, w.name, w.status,
                w.currentWorkload, w.reputationScore, w.zoneId, now, now);
    }

    private List<Worker> mapWorkerList(List<WorkerEntity> entities) {
        if (entities == null) return new ArrayList<>();
        List<Worker> result = new ArrayList<>(entities.size());
        for (WorkerEntity e : entities) {
            result.add(mapWorkerToDomain(e));
        }
        return result;
    }

    // --- ReputationEvent mapping ---

    private ReputationEvent mapEventToDomain(ReputationEventEntity e) {
        return new ReputationEvent(e.id, e.workerId, e.eventType, e.delta, e.taskId, e.notes);
    }

    private ReputationEventEntity mapEventToEntity(ReputationEvent r) {
        return new ReputationEventEntity(r.id, r.workerId, r.eventType, r.delta,
                r.taskId, r.notes, System.currentTimeMillis());
    }

    private List<ReputationEvent> mapEventList(List<ReputationEventEntity> entities) {
        if (entities == null) return new ArrayList<>();
        List<ReputationEvent> result = new ArrayList<>(entities.size());
        for (ReputationEventEntity e : entities) {
            result.add(mapEventToDomain(e));
        }
        return result;
    }
}
