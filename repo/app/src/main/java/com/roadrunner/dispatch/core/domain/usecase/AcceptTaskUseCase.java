package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.core.domain.repository.AuditLogRepository;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.core.domain.repository.WorkerRepository;

import com.roadrunner.dispatch.core.util.AppLogger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AcceptTaskUseCase {
    private static final long MUTEX_TIMEOUT_MS = 3000;

    // In-memory per-task mutex to prevent concurrent claims on the same task.
    private static final Map<String, Long> taskMutex = new ConcurrentHashMap<>();

    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final AuditLogRepository auditLogRepository;

    public AcceptTaskUseCase(TaskRepository taskRepository, WorkerRepository workerRepository,
                              AuditLogRepository auditLogRepository) {
        this.taskRepository = taskRepository;
        this.workerRepository = workerRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Attempt to claim a task for a worker.
     *
     * <p>Guards against race conditions with a per-task in-memory mutex (3-second window).
     * The unique index on (task_id, accepted_by) in task_acceptances provides a second
     * line of defence at the database level.
     *
     * @param taskId    ID of the task to claim
     * @param workerId  ID of the claiming worker
     * @param actorRole Role of the actor; must be "WORKER"
     */
    public Result<Task> execute(String taskId, String workerId, String actorRole, String orgId) {
        AppLogger.info("Task", "acceptTask taskId=" + AppLogger.mask(taskId) + " role=" + actorRole + " org=" + AppLogger.mask(orgId));
        if ("DISPATCHER".equals(actorRole)) {
            // Dispatcher assigns a specific worker directly — no mutex needed.
            long now = System.currentTimeMillis();
            Task task = taskRepository.getByIdScoped(taskId, orgId);
            if (task == null) {
                return Result.failure("Task not found");
            }
            if (!"OPEN".equals(task.status)) {
                return Result.failure("Task is no longer available. Current status: " + task.status);
            }
            if (!"ASSIGNED".equals(task.mode)) {
                return Result.failure("Dispatchers can only assign tasks in ASSIGNED mode. Task mode: " + task.mode);
            }

            Worker workerObj = workerRepository.getByIdScoped(workerId, orgId);
            if (workerObj == null) {
                return Result.failure("Worker not found in this organisation");
            }

            Task updated = new Task(
                    task.id, task.orgId, task.title, task.description,
                    "ASSIGNED", task.mode, task.priority, task.zoneId,
                    task.windowStart, task.windowEnd, workerId, task.createdBy
            );

            AuditLogEntry auditEntry = new AuditLogEntry(
                UUID.randomUUID().toString(), task.orgId, workerId, "TASK_ACCEPTED",
                "TASK", taskId,
                "{\"workerId\":\"" + workerId + "\",\"assignedBy\":\"DISPATCHER\"}",
                null, now
            );
            try {
                taskRepository.claimTaskWithSideEffects(
                        UUID.randomUUID().toString(), updated, workerId, now, 1, orgId, auditEntry
                );
            } catch (Exception e) {
                return Result.failure("Task already claimed by another worker");
            }

            return Result.success(updated);
        } else if ("WORKER".equals(actorRole)) {
            long now = System.currentTimeMillis();
            Long existingLock = taskMutex.get(taskId);
            if (existingLock != null && (now - existingLock) < MUTEX_TIMEOUT_MS) {
                AppLogger.warn("Task", "Concurrent claim attempt on task=" + AppLogger.mask(taskId));
                return Result.failure("Task claim in progress. Please try again in a moment.");
            }
            taskMutex.put(taskId, now);

            try {
                Task task = taskRepository.getByIdScoped(taskId, orgId);
                if (task == null) {
                    return Result.failure("Task not found");
                }
                if (!"OPEN".equals(task.status)) {
                    return Result.failure("Task is no longer available. Current status: " + task.status);
                }
                if (!"GRAB_ORDER".equals(task.mode)) {
                    return Result.failure("Workers can only claim tasks in GRAB_ORDER mode. Task mode: " + task.mode);
                }

                Worker workerObj = workerRepository.getByIdScoped(workerId, orgId);
                if (workerObj == null) {
                    return Result.failure("Worker not found in this organisation");
                }

                if (taskRepository.hasAcceptance(taskId, workerId)) {
                    return Result.failure("You have already claimed this task");
                }

                Task updated = new Task(
                        task.id, task.orgId, task.title, task.description,
                        "ASSIGNED", task.mode, task.priority, task.zoneId,
                        task.windowStart, task.windowEnd, workerId, task.createdBy
                );

                // Atomic: acceptance record, task status update, workload adjustment,
                // and audit log all in one transaction.
                // If the UNIQUE constraint fires (race condition) the transaction rolls back.
                AuditLogEntry auditEntry = new AuditLogEntry(
                    UUID.randomUUID().toString(), task.orgId, workerId, "TASK_ACCEPTED",
                    "TASK", taskId,
                    "{\"workerId\":\"" + workerId + "\"}",
                    null, now
                );
                try {
                    taskRepository.claimTaskWithSideEffects(
                            UUID.randomUUID().toString(), updated, workerId, now, 1, orgId, auditEntry
                    );
                } catch (Exception e) {
                    return Result.failure("Task already claimed by another worker");
                }

                return Result.success(updated);
            } finally {
                // Leave mutex in place for MUTEX_TIMEOUT_MS to throttle rapid retries.
            }
        } else {
            return Result.failure("Unauthorized: role must be WORKER to accept tasks");
        }
    }
}
