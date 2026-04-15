package com.roadrunner.dispatch.infrastructure.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.AuditLogDao;
import com.roadrunner.dispatch.infrastructure.db.dao.ReputationEventDao;
import com.roadrunner.dispatch.infrastructure.db.dao.TaskAcceptanceDao;
import com.roadrunner.dispatch.infrastructure.db.dao.TaskDao;
import com.roadrunner.dispatch.infrastructure.db.dao.WorkerDao;
import com.roadrunner.dispatch.infrastructure.db.entity.AuditLogEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ReputationEventEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.TaskAcceptanceEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.TaskEntity;

import java.util.ArrayList;
import java.util.List;

public class TaskRepositoryImpl implements TaskRepository {

    private final AppDatabase db;
    private final TaskDao taskDao;
    private final TaskAcceptanceDao taskAcceptanceDao;
    private final WorkerDao workerDao;
    private final ReputationEventDao reputationEventDao;
    private final AuditLogDao auditLogDao;

    public TaskRepositoryImpl(AppDatabase db, TaskDao taskDao, TaskAcceptanceDao taskAcceptanceDao) {
        this.db = db;
        this.taskDao = taskDao;
        this.taskAcceptanceDao = taskAcceptanceDao;
        this.workerDao = db.workerDao();
        this.reputationEventDao = db.reputationEventDao();
        this.auditLogDao = db.auditLogDao();
    }

    @Override
    public LiveData<List<Task>> getTasks(String orgId) {
        return Transformations.map(taskDao.getTasks(orgId), this::mapList);
    }

    @Override
    public LiveData<List<Task>> getTasksByStatus(String orgId, String status) {
        return Transformations.map(taskDao.getTasksByStatus(orgId, status), this::mapList);
    }

    @Override
    public List<Task> getOpenTasks(String orgId, String mode, long now) {
        List<TaskEntity> entities = taskDao.getOpenTasksByMode(orgId, mode, now);
        return mapList(entities);
    }

    @Override
    public Task getByIdScoped(String id, String orgId) {
        TaskEntity entity = taskDao.findByIdAndOrg(id, orgId);
        return entity != null ? mapToDomain(entity) : null;
    }

    @Override
    public void insert(Task task) {
        taskDao.insert(mapToEntity(task));
    }

    @Override
    public void update(Task task) {
        taskDao.update(mapToEntity(task));
    }

    @Override
    public void updateTask(Task task) {
        taskDao.update(mapToEntity(task));
    }

    @Override
    public boolean hasAcceptance(String taskId, String workerId) {
        return taskAcceptanceDao.findByTaskAndWorker(taskId, workerId) != null;
    }

    @Override
    public void insertAcceptance(String id, String taskId, String workerId, long acceptedAt) {
        TaskAcceptanceEntity entity = new TaskAcceptanceEntity(
                id, taskId, workerId, acceptedAt, "ACCEPTED"
        );
        taskAcceptanceDao.insert(entity);
    }

    /**
     * Atomically write the acceptance record and update the task status to ASSIGNED in
     * a single Room/SQLite transaction. If either write fails (e.g. UNIQUE violation on
     * the acceptance row) the transaction is rolled back, leaving no partial state.
     */
    @Override
    public void claimTask(String acceptanceId, Task claimedTask, String workerId, long acceptedAt) {
        TaskAcceptanceEntity acceptance = new TaskAcceptanceEntity(
                acceptanceId, claimedTask.id, workerId, acceptedAt, "ACCEPTED"
        );
        db.runInTransaction(() -> {
            int rows = taskDao.claimIfOpen(claimedTask.id, workerId, acceptedAt);
            if (rows == 0) {
                throw new IllegalStateException("Task already claimed");
            }
            taskAcceptanceDao.insert(acceptance);   // throws on UNIQUE violation → rolls back
        });
    }

    /**
     * Atomically perform all task-acceptance side-effects in a single transaction:
     * update the task to ASSIGNED, insert the acceptance record, adjust the worker's
     * workload, and write the audit log entry. If any step fails the entire
     * transaction rolls back.
     */
    @Override
    public void claimTaskWithSideEffects(String acceptanceId, Task claimedTask, String workerId,
            long acceptedAt, int workloadDelta, String orgId, AuditLogEntry auditEntry) {
        TaskAcceptanceEntity acceptance = new TaskAcceptanceEntity(
                acceptanceId, claimedTask.id, workerId, acceptedAt, "ACCEPTED"
        );
        AuditLogEntity auditEntity = new AuditLogEntity(
                auditEntry.id, auditEntry.orgId, auditEntry.actorId, auditEntry.action,
                auditEntry.targetType, auditEntry.targetId, auditEntry.details,
                auditEntry.caseId, auditEntry.createdAt
        );
        db.runInTransaction(() -> {
            int rows = taskDao.claimIfOpen(claimedTask.id, workerId, acceptedAt);
            if (rows == 0) {
                throw new IllegalStateException("Task already claimed");
            }
            taskAcceptanceDao.insert(acceptance);
            workerDao.adjustWorkload(workerId, workloadDelta, orgId);
            auditLogDao.insert(auditEntity);
        });
    }

    /**
     * Atomically complete a task and update all related worker state in one
     * transaction: update the task status to COMPLETED, decrement workload, insert
     * the reputation event, and update the reputation score. If any step fails the
     * entire transaction rolls back.
     */
    @Override
    public void completeTaskWithSideEffects(Task completed, String workerId, int workloadDelta,
            String orgId, ReputationEvent reputationEvent, double newRepScore) {
        ReputationEventEntity eventEntity = new ReputationEventEntity(
                reputationEvent.id, reputationEvent.workerId, reputationEvent.eventType,
                reputationEvent.delta, reputationEvent.taskId, reputationEvent.notes,
                System.currentTimeMillis()
        );
        // newRepScore is accepted for interface compatibility but intentionally ignored here.
        // The fresh score is computed INSIDE the transaction, AFTER the event is inserted,
        // so the average already includes the just-inserted event and is never stale.
        db.runInTransaction(() -> {
            taskDao.update(mapToEntity(completed));
            workerDao.adjustWorkload(workerId, workloadDelta, orgId);
            reputationEventDao.insert(eventEntity);
            // Compute fresh score from the average that now includes the just-inserted event
            double freshAvg = reputationEventDao.getAverageScore(workerId);
            double freshScore = Math.max(0.0, Math.min(5.0, 3.0 + freshAvg));
            workerDao.updateReputationScore(workerId, freshScore, orgId);
        });
    }

    @Override
    public List<Task> getWorkerActiveTasks(String orgId, String workerId) {
        return mapList(taskDao.getWorkerActiveTasks(orgId, workerId));
    }

    @Override
    public LiveData<List<Task>> getWorkerActiveTasksLive(String orgId, String workerId) {
        return Transformations.map(taskDao.getWorkerActiveTasksLive(orgId, workerId), this::mapList);
    }

    // --- Mapping helpers ---

    private Task mapToDomain(TaskEntity e) {
        // priority is stored as int in entity; domain model uses String
        String priorityStr = String.valueOf(e.priority);
        return new Task(e.id, e.orgId, e.title, e.description, e.status, e.mode,
                priorityStr, e.zoneId, e.windowStart, e.windowEnd,
                e.assignedWorkerId, e.createdBy);
    }

    private TaskEntity mapToEntity(Task t) {
        long now = System.currentTimeMillis();
        int priorityInt;
        try {
            priorityInt = Integer.parseInt(t.priority);
        } catch (NumberFormatException e) {
            priorityInt = 0;
        }
        return new TaskEntity(t.id, t.orgId, t.title,
                t.description != null ? t.description : "",
                t.status, t.mode, priorityInt, t.zoneId,
                t.windowStart, t.windowEnd, t.assignedWorkerId,
                t.createdBy, now, now);
    }

    private List<Task> mapList(List<TaskEntity> entities) {
        if (entities == null) return new ArrayList<>();
        List<Task> result = new ArrayList<>(entities.size());
        for (TaskEntity e : entities) {
            result.add(mapToDomain(e));
        }
        return result;
    }
}
