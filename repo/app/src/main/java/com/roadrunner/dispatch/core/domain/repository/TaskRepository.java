package com.roadrunner.dispatch.core.domain.repository;

import androidx.lifecycle.LiveData;
import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import com.roadrunner.dispatch.core.domain.model.Task;
import java.util.List;

public interface TaskRepository {
    LiveData<List<Task>> getTasks(String orgId);
    LiveData<List<Task>> getTasksByStatus(String orgId, String status);
    List<Task> getOpenTasks(String orgId, String mode, long now);
    Task getByIdScoped(String id, String orgId);
    void insert(Task task);
    void update(Task task);
    void updateTask(Task task);
    boolean hasAcceptance(String taskId, String workerId);
    void insertAcceptance(String id, String taskId, String workerId, long acceptedAt);

    /**
     * Atomically insert the acceptance record and update the task status to ASSIGNED.
     * Both writes run inside a single database transaction so a failure leaves no
     * partial state (phantom acceptance with an OPEN task).
     *
     * @throws RuntimeException (propagated from Room) if the acceptance already exists
     *                          or the task row cannot be updated.
     */
    void claimTask(String acceptanceId, Task claimedTask, String workerId, long acceptedAt);

    /**
     * Atomically perform the full task-acceptance side-effects in one transaction:
     * insert the acceptance record, update the task to ASSIGNED, increment the
     * worker's workload, and write the audit log entry.
     *
     * @throws RuntimeException if the task is already claimed (UNIQUE violation) or
     *                          any write fails.
     */
    void claimTaskWithSideEffects(String acceptanceId, Task claimedTask, String workerId,
            long acceptedAt, int workloadDelta, String orgId, AuditLogEntry auditEntry);

    /**
     * Atomically mark the task COMPLETED, decrement the worker's workload, record a
     * reputation event, and update the worker's reputation score — all in one
     * database transaction.
     */
    void completeTaskWithSideEffects(Task completed, String workerId, int workloadDelta,
            String orgId, ReputationEvent reputationEvent, double newRepScore);

    List<Task> getWorkerActiveTasks(String orgId, String workerId);
    LiveData<List<Task>> getWorkerActiveTasksLive(String orgId, String workerId);
}
