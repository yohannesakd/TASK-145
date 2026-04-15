package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.core.domain.repository.WorkerRepository;
import com.roadrunner.dispatch.core.domain.usecase.CompleteTaskUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CompleteTaskUseCaseTest {

    private StubTaskRepository taskRepo;
    private StubWorkerRepository workerRepo;
    private CompleteTaskUseCase useCase;

    @Before
    public void setUp() {
        taskRepo   = new StubTaskRepository();
        workerRepo = new StubWorkerRepository();
        taskRepo.workerRepoRef = workerRepo;
        useCase    = new CompleteTaskUseCase(taskRepo, workerRepo);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Task taskWithStatus(String id, String status, String assignedWorkerId) {
        return new Task(id, "org1", "Deliver Box", "desc",
                status, "ASSIGNED", "5", "zone1",
                System.currentTimeMillis(), System.currentTimeMillis() + 3_600_000L,
                assignedWorkerId, "creator1");
    }

    private Worker worker(String id) {
        return new Worker(id, "u-" + id, "org1", "Worker " + id, "AVAILABLE", 3, 3.0, "zone1");
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void completeAssignedTask_statusBecomesCompleted() {
        taskRepo.insert(taskWithStatus("t1", "ASSIGNED", "w1"));
        workerRepo.insert(worker("w1"));

        Result<Task> result = useCase.execute("t1", "w1", "WORKER", "org1");

        assertTrue(result.isSuccess());
        assertEquals("COMPLETED", result.getData().status);
    }

    @Test
    public void completeInProgressTask_statusBecomesCompleted() {
        taskRepo.insert(taskWithStatus("t1", "IN_PROGRESS", "w1"));
        workerRepo.insert(worker("w1"));

        Result<Task> result = useCase.execute("t1", "w1", "WORKER", "org1");

        assertTrue(result.isSuccess());
        assertEquals("COMPLETED", result.getData().status);
    }

    @Test
    public void completeOpenTask_failure_wrongStatus() {
        taskRepo.insert(taskWithStatus("t1", "OPEN", null));

        Result<Task> result = useCase.execute("t1", "w1", "WORKER", "org1");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("cannot be completed") ||
                   result.getFirstError().toLowerCase().contains("status"));
    }

    @Test
    public void completeTaskAssignedToDifferentWorker_failure() {
        taskRepo.insert(taskWithStatus("t1", "ASSIGNED", "w-other"));

        Result<Task> result = useCase.execute("t1", "w1", "WORKER", "org1");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("not assigned"));
    }

    @Test
    public void taskNotFound_failure() {
        Result<Task> result = useCase.execute("nonexistent", "w1", "WORKER", "org1");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("Task not found"));
    }

    @Test
    public void workloadDecrementedOnCompletion() {
        taskRepo.insert(taskWithStatus("t1", "ASSIGNED", "w1"));
        workerRepo.insert(worker("w1"));

        useCase.execute("t1", "w1", "WORKER", "org1");

        assertEquals(-1, workerRepo.workloadAdjustments.getOrDefault("w1", 0).intValue());
    }

    @Test
    public void reputationEventCreated_withPositiveDelta() {
        taskRepo.insert(taskWithStatus("t1", "ASSIGNED", "w1"));
        workerRepo.insert(worker("w1"));

        useCase.execute("t1", "w1", "WORKER", "org1");

        assertFalse(workerRepo.reputationEvents.isEmpty());
        ReputationEvent event = workerRepo.reputationEvents.get(0);
        assertTrue(event.delta > 0);
        assertEquals("w1", event.workerId);
        assertEquals("TASK_COMPLETED", event.eventType);
    }

    @Test
    public void dispatcherRole_rejected() {
        taskRepo.insert(taskWithStatus("t1", "ASSIGNED", "w1"));
        workerRepo.insert(worker("w1"));

        Result<Task> result = useCase.execute("t1", "w1", "DISPATCHER", "org1");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role"));
    }

    @Test
    public void adminRole_rejected() {
        taskRepo.insert(taskWithStatus("t1", "ASSIGNED", "w1"));
        workerRepo.insert(worker("w1"));

        Result<Task> result = useCase.execute("t1", "w1", "ADMIN", "org1");

        assertFalse(result.isSuccess());
    }

    @Test
    public void reputationScore_computedFreshFromAverage() {
        taskRepo.insert(taskWithStatus("t-rep", "ASSIGNED", "w-rep"));
        workerRepo.insert(worker("w-rep"));
        // Simulate a worker with a positive average reputation delta
        workerRepo.fixedAvgReputation = 0.8;

        useCase.execute("t-rep", "w-rep", "WORKER", "org1");

        // The stub mirrors the real repo: score = clamp(3.0 + avgDelta, 0, 5)
        // With avgDelta=0.8 → expected score = 3.8
        Double storedScore = workerRepo.reputationScores.get("w-rep");
        assertNotNull("Reputation score should be updated", storedScore);
        assertEquals(3.8, storedScore, 0.001);
    }

    @Test
    public void nullOrgId_failure() {
        Result<Task> result = useCase.execute("t-any", "w1", "WORKER", null);
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("organisation"));
    }

    @Test
    public void reputationScore_clampedToMax5() {
        taskRepo.insert(taskWithStatus("t-clamp", "ASSIGNED", "w-clamp"));
        workerRepo.insert(worker("w-clamp"));
        // Average delta so high that 3.0 + avg > 5.0
        workerRepo.fixedAvgReputation = 3.5;

        useCase.execute("t-clamp", "w-clamp", "WORKER", "org1");

        Double storedScore = workerRepo.reputationScores.get("w-clamp");
        assertNotNull(storedScore);
        assertEquals("Score must be clamped to 5.0", 5.0, storedScore, 0.001);
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubTaskRepository implements TaskRepository {
        private final Map<String, Task> tasks = new HashMap<>();
        // Back-reference wired during setUp to simulate transactional side-effects
        StubWorkerRepository workerRepoRef;

        @Override public Task getByIdScoped(String id, String orgId) { return tasks.get(id); }
        @Override public void insert(Task task) { tasks.put(task.id, task); }
        @Override public void update(Task task) { tasks.put(task.id, task); }
        @Override public void updateTask(Task task) { tasks.put(task.id, task); }
        @Override public boolean hasAcceptance(String taskId, String workerId) { return false; }
        @Override public void insertAcceptance(String id, String taskId, String workerId, long acceptedAt) {}
        @Override public void claimTask(String acceptanceId, Task claimedTask, String workerId, long acceptedAt) { tasks.put(claimedTask.id, claimedTask); }
        @Override public void claimTaskWithSideEffects(String acceptanceId, Task claimedTask, String workerId,
                long acceptedAt, int workloadDelta, String orgId, AuditLogEntry auditEntry) {
            tasks.put(claimedTask.id, claimedTask);
        }
        @Override public void completeTaskWithSideEffects(Task completed, String workerId, int workloadDelta,
                String orgId, ReputationEvent reputationEvent, double newRepScore) {
            tasks.put(completed.id, completed);
            if (workerRepoRef != null) {
                workerRepoRef.adjustWorkload(workerId, workloadDelta, orgId);
                workerRepoRef.addReputationEvent(reputationEvent);
                // Mirror real TaskRepositoryImpl: compute fresh score AFTER inserting event
                double freshAvg = workerRepoRef.getAverageReputation(workerId);
                double freshScore = Math.max(0.0, Math.min(5.0, 3.0 + freshAvg));
                workerRepoRef.updateReputationScore(workerId, freshScore, orgId);
            }
        }
        @Override public List<Task> getOpenTasks(String orgId, String mode, long now) { return new ArrayList<>(); }
        @Override public List<Task> getWorkerActiveTasks(String orgId, String workerId) { return new ArrayList<>(); }
        @Override public LiveData<List<Task>> getWorkerActiveTasksLive(String orgId, String workerId) { return null; }
        @Override public LiveData<List<Task>> getTasks(String orgId) { return null; }
        @Override public LiveData<List<Task>> getTasksByStatus(String orgId, String status) { return null; }
    }

    private static class StubWorkerRepository implements WorkerRepository {
        private final Map<String, Worker> workers = new HashMap<>();
        final Map<String, Integer> workloadAdjustments = new HashMap<>();
        final List<ReputationEvent> reputationEvents = new ArrayList<>();
        final Map<String, Double> reputationScores = new HashMap<>();
        double fixedAvgReputation = 0.5;

        @Override public Worker getByIdScoped(String id, String orgId) { return workers.get(id); }
        @Override public Worker getByUserIdScoped(String userId, String orgId) { return null; }
        @Override public void insert(Worker worker) { workers.put(worker.id, worker); }
        @Override public void update(Worker worker) { workers.put(worker.id, worker); }

        @Override
        public void adjustWorkload(String workerId, int delta, String orgId) {
            workloadAdjustments.merge(workerId, delta, Integer::sum);
        }

        @Override
        public void updateReputationScore(String workerId, double score, String orgId) {
            reputationScores.put(workerId, score);
        }

        @Override
        public void addReputationEvent(ReputationEvent event) {
            reputationEvents.add(event);
        }

        @Override public double getAverageReputation(String workerId) { return fixedAvgReputation; }

        @Override
        public List<Worker> getWorkersByStatus(String orgId, String status) {
            List<Worker> result = new ArrayList<>();
            for (Worker w : workers.values()) {
                if (w.orgId.equals(orgId) && w.status.equals(status)) result.add(w);
            }
            return result;
        }

        @Override public LiveData<List<Worker>> getWorkers(String orgId) { return null; }
        @Override public LiveData<List<ReputationEvent>> getReputationEvents(String workerId) { return null; }
    }
}
