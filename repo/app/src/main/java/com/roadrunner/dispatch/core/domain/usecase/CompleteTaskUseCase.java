package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.core.domain.repository.WorkerRepository;

import java.util.UUID;

public class CompleteTaskUseCase {
    private static final double COMPLETION_DELTA = 0.5;

    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;

    public CompleteTaskUseCase(TaskRepository taskRepository, WorkerRepository workerRepository) {
        this.taskRepository = taskRepository;
        this.workerRepository = workerRepository;
    }

    /**
     * Mark a task as COMPLETED and update the assigned worker's reputation.
     *
     * @param taskId    ID of the task to complete
     * @param workerId  ID of the worker completing the task
     * @param actorRole Role of the actor; must be "WORKER"
     * @param orgId     Organisation scope; must not be null or empty
     */
    public Result<Task> execute(String taskId, String workerId, String actorRole, String orgId) {
        if (!"WORKER".equals(actorRole)) {
            return Result.failure("Unauthorized: role must be WORKER to complete tasks");
        }

        if (orgId == null || orgId.isEmpty()) {
            return Result.failure("Organisation ID is required");
        }

        Task task = taskRepository.getByIdScoped(taskId, orgId);
        if (task == null) {
            return Result.failure("Task not found");
        }
        if (!"IN_PROGRESS".equals(task.status) && !"ASSIGNED".equals(task.status)) {
            return Result.failure("Task cannot be completed. Current status: " + task.status);
        }
        if (task.assignedWorkerId == null || !task.assignedWorkerId.equals(workerId)) {
            return Result.failure("You are not assigned to this task");
        }

        Task completed = new Task(
                task.id, task.orgId, task.title, task.description,
                "COMPLETED", task.mode, task.priority, task.zoneId,
                task.windowStart, task.windowEnd, task.assignedWorkerId, task.createdBy
        );

        ReputationEvent event = new ReputationEvent(
                UUID.randomUUID().toString(),
                workerId,
                "TASK_COMPLETED",
                COMPLETION_DELTA,
                taskId,
                "Completed task: " + task.title
        );

        // Score is computed inside the transaction after the event is inserted,
        // ensuring it reflects the latest data. Pass 0.0 as placeholder.
        double newScore = 0.0;

        // Atomically: update task to COMPLETED, decrement workload, record reputation
        // event, and update reputation score — all in one transaction.
        taskRepository.completeTaskWithSideEffects(completed, workerId, -1, orgId, event, newScore);

        return Result.success(completed);
    }
}
