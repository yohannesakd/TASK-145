package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.core.domain.repository.AuditLogRepository;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.core.domain.repository.WorkerRepository;
import com.roadrunner.dispatch.core.domain.usecase.AcceptTaskUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class AcceptTaskUseCaseTest {

    private StubTaskRepository taskRepo;
    private StubWorkerRepository workerRepo;
    private StubAuditLogRepository auditRepo;
    private AcceptTaskUseCase useCase;

    @Before
    public void setUp() {
        taskRepo = new StubTaskRepository();
        workerRepo = new StubWorkerRepository();
        auditRepo = new StubAuditLogRepository();
        taskRepo.workerRepoRef = workerRepo;
        taskRepo.auditRepoRef = auditRepo;
        useCase = new AcceptTaskUseCase(taskRepo, workerRepo, auditRepo);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Task openTask(String id) {
        return new Task(id, "org1", "Deliver Package", "desc",
                "OPEN", "GRAB_ORDER", "5", "zone1",
                System.currentTimeMillis(), System.currentTimeMillis() + 3600000L,
                null, "creator1");
    }

    private Task taskWithStatus(String id, String status) {
        return new Task(id, "org1", "Task " + id, "desc",
                status, "GRAB_ORDER", "5", "zone1",
                System.currentTimeMillis(), System.currentTimeMillis() + 3600000L,
                status.equals("ASSIGNED") ? "w1" : null, "creator1");
    }

    private Worker worker(String id) {
        return new Worker(id, "u-" + id, "org1", "Worker " + id, "AVAILABLE", 2, 4.0, "zone1");
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void acceptOpenTask_success_taskBecomesAssigned() {
        taskRepo.insert(openTask("t1"));
        workerRepo.insert(worker("w1"));

        Result<Task> result = useCase.execute("t1", "w1", "WORKER", "org1");

        assertTrue(result.isSuccess());
        Task updated = result.getData();
        assertEquals("ASSIGNED", updated.status);
        assertEquals("w1", updated.assignedWorkerId);
    }

    @Test
    public void acceptOpenTask_success_workerWorkloadIncremented() {
        taskRepo.insert(openTask("t2"));
        workerRepo.insert(worker("w2"));

        useCase.execute("t2", "w2", "WORKER", "org1");

        assertEquals(Integer.valueOf(1), workerRepo.workloadAdjustments.getOrDefault("w2", 0));
    }

    @Test
    public void acceptOpenTask_acceptanceRecordCreated() {
        taskRepo.insert(openTask("t3"));
        workerRepo.insert(worker("w3"));

        useCase.execute("t3", "w3", "WORKER", "org1");

        assertTrue("Acceptance should be recorded",
                taskRepo.hasAcceptance("t3", "w3"));
    }

    @Test
    public void acceptAssignedTask_failure_notAvailable() {
        taskRepo.insert(taskWithStatus("t4", "ASSIGNED"));

        Result<Task> result = useCase.execute("t4", "w1", "WORKER", "org1");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("no longer available") ||
                result.getFirstError().toLowerCase().contains("current status"));
    }

    @Test
    public void acceptCompletedTask_failure() {
        taskRepo.insert(taskWithStatus("t5", "COMPLETED"));

        Result<Task> result = useCase.execute("t5", "w1", "WORKER", "org1");

        assertFalse(result.isSuccess());
    }

    @Test
    public void acceptCancelledTask_failure() {
        taskRepo.insert(taskWithStatus("t6", "CANCELLED"));

        Result<Task> result = useCase.execute("t6", "w1", "WORKER", "org1");

        assertFalse(result.isSuccess());
    }

    @Test
    public void taskNotFound_failure() {
        Result<Task> result = useCase.execute("nonexistent", "w1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("not found"));
    }

    @Test
    public void workerAlreadyAcceptedThisTask_failure() {
        taskRepo.insert(openTask("t7"));
        workerRepo.insert(worker("w1"));
        // Record prior acceptance for this worker
        taskRepo.recordAcceptance("t7", "w1");

        Result<Task> result = useCase.execute("t7", "w1", "WORKER", "org1");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("already claimed"));
    }

    @Test
    public void dispatcherRole_canAssign() {
        // Dispatcher can only assign tasks in ASSIGNED mode
        Task task = new Task("t9", "org1", "Deliver Package", "desc",
                "OPEN", "ASSIGNED", "5", "zone1",
                System.currentTimeMillis(), System.currentTimeMillis() + 3600000L,
                null, "creator1");
        taskRepo.insert(task);
        workerRepo.insert(worker("w9"));

        Result<Task> result = useCase.execute("t9", "w9", "DISPATCHER", "org1");
        assertTrue(result.isSuccess());
    }

    @Test
    public void worker_cannotClaimAssignedModeTask_failure() {
        // Create a task with ASSIGNED mode — unique ID to avoid static mutex collision
        Task task = new Task("t-assigned-mode", "org1", "Test", "desc", "OPEN", "ASSIGNED", "5", "z1",
                System.currentTimeMillis(), System.currentTimeMillis() + 86400000, null, "creator1");
        taskRepo.insert(task);

        Result<Task> result = useCase.execute("t-assigned-mode", "w1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("grab_order"));
    }

    @Test
    public void dispatcher_cannotAssignGrabOrderModeTask_failure() {
        // Create a task with GRAB_ORDER mode - dispatcher should not be able to assign it
        Task task = openTask("t-grab");
        taskRepo.insert(task);

        Result<Task> result = useCase.execute("t-grab", "w1", "DISPATCHER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("assigned mode"));
    }

    @Test
    public void adminRole_rejected() {
        taskRepo.insert(openTask("t10"));
        workerRepo.insert(worker("w10"));

        Result<Task> result = useCase.execute("t10", "w10", "ADMIN", "org1");
        assertFalse(result.isSuccess());
    }

    @Test
    public void differentWorker_canAcceptSameOpenTask_ifNotYetAccepted() {
        taskRepo.insert(openTask("t8"));
        workerRepo.insert(worker("w99"));

        Result<Task> result = useCase.execute("t8", "w99", "WORKER", "org1");

        assertTrue(result.isSuccess());
    }

    @Test
    public void auditLogWrittenOnAccept() {
        taskRepo.insert(openTask("t11"));
        workerRepo.insert(worker("w11"));

        useCase.execute("t11", "w11", "WORKER", "org1");

        assertFalse(auditRepo.logs.isEmpty());
        assertEquals("TASK_ACCEPTED", auditRepo.logs.get(0).action);
    }

    @Test
    public void wrongOrg_rejectsAcceptance() {
        taskRepo.insert(openTask("t_cross"));
        workerRepo.insert(worker("w_cross"));

        Result<Task> result = useCase.execute("t_cross", "w_cross", "WORKER", "wrong_org");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("not found"));
    }

    @Test
    public void wrongOrg_rejectsDispatcherAssignment() {
        taskRepo.insert(openTask("t_cross2"));
        workerRepo.insert(worker("w_cross2"));

        Result<Task> result = useCase.execute("t_cross2", "w_cross2", "DISPATCHER", "wrong_org");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("not found"));
    }

    @Test
    public void dispatcher_taskAlreadyAssigned_failure() {
        // Branch: DISPATCHER path, task status not OPEN
        Task assigned = new Task("t-da", "org1", "Task", "desc", "ASSIGNED", "ASSIGNED", "5", "zone1",
            System.currentTimeMillis(), System.currentTimeMillis() + 3_600_000L, "w1", "creator1");
        taskRepo.tasks.put("t-da", assigned);
        workerRepo.workers.put("w2", new Worker("w2", "u-w2", "org1", "Worker 2", "AVAILABLE", 2, 4.0, "zone1"));
        Result<Task> result = useCase.execute("t-da", "w2", "DISPATCHER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("no longer available")
                || result.getFirstError().toLowerCase().contains("status"));
    }

    @Test
    public void dispatcher_workerNotFoundInOrg_failure() {
        // Branch: DISPATCHER path, worker not found
        Task open = new Task("t-dw", "org1", "Task", "desc", "OPEN", "ASSIGNED", "5", "zone1",
            System.currentTimeMillis(), System.currentTimeMillis() + 3_600_000L, null, "creator1");
        taskRepo.tasks.put("t-dw", open);
        // Don't add any worker
        Result<Task> result = useCase.execute("t-dw", "ghost-worker", "DISPATCHER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("worker")
                || result.getFirstError().toLowerCase().contains("not found"));
    }

    @Test
    public void dispatcher_raceCondition_failure() {
        // Branch: DISPATCHER path, claimTaskWithSideEffects throws
        Task open = new Task("t-dr", "org1", "Task", "desc", "OPEN", "ASSIGNED", "5", "zone1",
            System.currentTimeMillis(), System.currentTimeMillis() + 3_600_000L, null, "creator1");
        taskRepo.tasks.put("t-dr", open);
        workerRepo.workers.put("w3", new Worker("w3", "u-w3", "org1", "Worker 3", "AVAILABLE", 2, 4.0, "zone1"));
        // Pre-record acceptance so claimTaskWithSideEffects throws
        taskRepo.recordAcceptance("t-dr", "w3");
        Result<Task> result = useCase.execute("t-dr", "w3", "DISPATCHER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("claimed")
                || result.getFirstError().toLowerCase().contains("already"));
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubTaskRepository implements TaskRepository {
        final Map<String, Task> tasks = new HashMap<>();
        // Set of "taskId:workerId" to track acceptances
        private final Set<String> acceptances = new HashSet<>();
        // Back-references wired during setUp to simulate transactional side-effects
        StubWorkerRepository workerRepoRef;
        StubAuditLogRepository auditRepoRef;

        @Override
        public Task getByIdScoped(String id, String orgId) {
            Task task = tasks.get(id);
            if (task != null && task.orgId != null && !task.orgId.equals(orgId)) return null;
            return task;
        }

        @Override
        public void insert(Task task) { tasks.put(task.id, task); }

        @Override
        public void update(Task task) { tasks.put(task.id, task); }

        @Override
        public void updateTask(Task task) { tasks.put(task.id, task); }

        @Override
        public boolean hasAcceptance(String taskId, String workerId) {
            return acceptances.contains(taskId + ":" + workerId);
        }

        @Override
        public void insertAcceptance(String id, String taskId, String workerId, long acceptedAt) {
            acceptances.add(taskId + ":" + workerId);
        }

        @Override
        public void claimTask(String acceptanceId, Task claimedTask, String workerId, long acceptedAt) {
            String key = claimedTask.id + ":" + workerId;
            if (acceptances.contains(key)) {
                throw new RuntimeException("UNIQUE constraint failed: task_acceptances.task_id, task_acceptances.accepted_by");
            }
            acceptances.add(key);
            tasks.put(claimedTask.id, claimedTask);
        }

        @Override
        public void claimTaskWithSideEffects(String acceptanceId, Task claimedTask, String workerId,
                long acceptedAt, int workloadDelta, String orgId, AuditLogEntry auditEntry) {
            String key = claimedTask.id + ":" + workerId;
            if (acceptances.contains(key)) {
                throw new RuntimeException("UNIQUE constraint failed: task_acceptances.task_id, task_acceptances.accepted_by");
            }
            acceptances.add(key);
            tasks.put(claimedTask.id, claimedTask);
            // Simulate workload adjustment and audit log (tracked via workerRepo/auditRepo in tests)
            workerRepoRef.adjustWorkload(workerId, workloadDelta, orgId);
            auditRepoRef.log(auditEntry);
        }

        @Override
        public void completeTaskWithSideEffects(Task completed, String workerId, int workloadDelta,
                String orgId, ReputationEvent reputationEvent, double newRepScore) {
            tasks.put(completed.id, completed);
        }

        void recordAcceptance(String taskId, String workerId) {
            acceptances.add(taskId + ":" + workerId);
        }

        @Override
        public LiveData<List<Task>> getTasks(String orgId) { return null; }

        @Override
        public LiveData<List<Task>> getTasksByStatus(String orgId, String status) { return null; }

        @Override
        public List<Task> getOpenTasks(String orgId, String mode, long now) { return new ArrayList<>(); }

        @Override
        public List<Task> getWorkerActiveTasks(String orgId, String workerId) { return new ArrayList<>(); }

        @Override
        public LiveData<List<Task>> getWorkerActiveTasksLive(String orgId, String workerId) { return null; }
    }

    private static class StubWorkerRepository implements WorkerRepository {
        final Map<String, Worker> workers = new HashMap<>();
        final Map<String, Integer> workloadAdjustments = new HashMap<>();
        private final Map<String, Double> reputationScores = new HashMap<>();
        private final List<ReputationEvent> events = new ArrayList<>();

        @Override
        public Worker getByIdScoped(String id, String orgId) { return workers.get(id); }

        @Override
        public Worker getByUserIdScoped(String userId, String orgId) {
            for (Worker w : workers.values()) if (w.userId.equals(userId)) return w;
            return null;
        }

        @Override
        public void insert(Worker worker) { workers.put(worker.id, worker); }

        @Override
        public void update(Worker worker) { workers.put(worker.id, worker); }

        @Override
        public void adjustWorkload(String workerId, int delta, String orgId) {
            workloadAdjustments.merge(workerId, delta, Integer::sum);
        }

        @Override
        public void updateReputationScore(String workerId, double score, String orgId) {
            reputationScores.put(workerId, score);
        }

        @Override
        public void addReputationEvent(ReputationEvent event) { events.add(event); }

        @Override
        public double getAverageReputation(String workerId) { return 0.5; }

        @Override
        public List<Worker> getWorkersByStatus(String orgId, String status) {
            List<Worker> result = new ArrayList<>();
            for (Worker w : workers.values()) {
                if (w.orgId.equals(orgId) && w.status.equals(status)) result.add(w);
            }
            return result;
        }

        @Override
        public LiveData<List<Worker>> getWorkers(String orgId) { return null; }

        @Override
        public LiveData<List<ReputationEvent>> getReputationEvents(String workerId) { return null; }
    }

    private static class StubAuditLogRepository implements AuditLogRepository {
        final List<com.roadrunner.dispatch.core.domain.model.AuditLogEntry> logs = new ArrayList<>();

        @Override
        public void log(com.roadrunner.dispatch.core.domain.model.AuditLogEntry entry) { logs.add(entry); }

        @Override
        public androidx.lifecycle.LiveData<java.util.List<com.roadrunner.dispatch.core.domain.model.AuditLogEntry>> getLogsForCase(String caseId, String orgId) { return null; }

        @Override
        public androidx.lifecycle.LiveData<java.util.List<com.roadrunner.dispatch.core.domain.model.AuditLogEntry>> getAllLogs(String orgId) { return null; }
    }
}
