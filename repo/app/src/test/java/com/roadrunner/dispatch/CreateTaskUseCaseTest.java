package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.core.domain.repository.SensitiveWordRepository;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.core.domain.repository.ZoneRepository;
import com.roadrunner.dispatch.core.domain.usecase.CreateTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ScanContentUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class CreateTaskUseCaseTest {

    private StubTaskRepository taskRepo;
    private StubZoneRepository zoneRepo;
    private CreateTaskUseCase useCase;
    private CreateTaskUseCase useCaseWithScan;

    private static final long NOW = System.currentTimeMillis();
    private static final long WINDOW_START = NOW + 60_000L;   // 1 min from now
    private static final long WINDOW_END   = NOW + 3_600_000L; // 1 hour from now

    @Before
    public void setUp() {
        taskRepo = new StubTaskRepository();
        zoneRepo = new StubZoneRepository();
        useCase = new CreateTaskUseCase(taskRepo, zoneRepo);
        // Pre-populate a valid zone
        zoneRepo.insert(new Zone("zone1", "org1", "Downtown", 3, "Main zone"));

        // Set up a scan-enabled use case with a stub sensitive-word repo
        StubSensitiveWordRepository sensitiveWordRepo = new StubSensitiveWordRepository();
        sensitiveWordRepo.zeroTolerance.add("badword");
        sensitiveWordRepo.allWords.add("flaggedword");
        ScanContentUseCase scanUseCase = new ScanContentUseCase(sensitiveWordRepo);
        useCaseWithScan = new CreateTaskUseCase(taskRepo, zoneRepo, scanUseCase);
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    public void validTask_GRAB_ORDER_success() {
        Result<Task> result = useCase.execute(
                "org1", "Deliver Package", "Take to 5th Ave",
                "GRAB_ORDER", 5, "zone1", WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertTrue(result.isSuccess());
        Task task = result.getData();
        assertNotNull(task);
        assertEquals("OPEN", task.status);
        assertEquals("GRAB_ORDER", task.mode);
        assertEquals("Deliver Package", task.title);
        assertEquals("5", task.priority);
        assertNull(task.assignedWorkerId);
    }

    @Test
    public void validTask_ASSIGNED_success() {
        Result<Task> result = useCase.execute(
                "org1", "Dispatch Run", "Downtown run",
                "ASSIGNED", 8, "zone1", WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertTrue(result.isSuccess());
        assertEquals("ASSIGNED", result.getData().mode);
    }

    @Test
    public void validTask_insertedIntoRepository() {
        useCase.execute("org1", "My Task", "desc", "GRAB_ORDER", 1, "zone1",
                WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertFalse("Task should be inserted into repository", taskRepo.tasks.isEmpty());
    }

    @Test
    public void validTask_idIsGeneratedAndNotNull() {
        Result<Task> result = useCase.execute("org1", "My Task", "desc", "GRAB_ORDER", 1, "zone1",
                WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertNotNull(result.getData().id);
        assertFalse(result.getData().id.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Title validation
    // -----------------------------------------------------------------------

    @Test
    public void missingTitle_null_failure() {
        Result<Task> result = useCase.execute("org1", null, "desc", "GRAB_ORDER", 1, "zone1",
                WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("title")));
    }

    @Test
    public void emptyTitle_failure() {
        Result<Task> result = useCase.execute("org1", "  ", "desc", "GRAB_ORDER", 1, "zone1",
                WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("title")));
    }

    // -----------------------------------------------------------------------
    // Mode validation
    // -----------------------------------------------------------------------

    @Test
    public void invalidMode_failure() {
        Result<Task> result = useCase.execute("org1", "My Task", "desc", "INVALID_MODE", 1, "zone1",
                WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("mode")));
    }

    @Test
    public void nullMode_failure() {
        Result<Task> result = useCase.execute("org1", "My Task", "desc", null, 1, "zone1",
                WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("mode")));
    }

    // -----------------------------------------------------------------------
    // Window validation
    // -----------------------------------------------------------------------

    @Test
    public void endBeforeStart_failure() {
        Result<Task> result = useCase.execute("org1", "My Task", "desc", "GRAB_ORDER", 1, "zone1",
                WINDOW_END, WINDOW_START, // reversed: end < start
                "user1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("end")));
    }

    @Test
    public void endEqualsStart_failure() {
        Result<Task> result = useCase.execute("org1", "My Task", "desc", "GRAB_ORDER", 1, "zone1",
                WINDOW_START, WINDOW_START, "user1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("end")));
    }

    @Test
    public void zeroWindowStart_failure() {
        Result<Task> result = useCase.execute("org1", "My Task", "desc", "GRAB_ORDER", 1, "zone1",
                0L, WINDOW_END, "user1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("window start")));
    }

    // -----------------------------------------------------------------------
    // Zone validation
    // -----------------------------------------------------------------------

    @Test
    public void zoneNotFound_failure() {
        Result<Task> result = useCase.execute("org1", "My Task", "desc", "GRAB_ORDER", 1, "zone-unknown",
                WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("zone not found"));
    }

    @Test
    public void nullZoneId_failure() {
        Result<Task> result = useCase.execute("org1", "My Task", "desc", "GRAB_ORDER", 1, null,
                WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("zone")));
    }

    // -----------------------------------------------------------------------
    // Multiple validation errors collected together
    // -----------------------------------------------------------------------

    @Test
    public void multipleValidationErrors_allReported() {
        // null title + invalid mode → two errors collected before zone check
        Result<Task> result = useCase.execute("org1", null, "desc", "BAD_MODE", 1, "zone1",
                WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertEquals("Expected 2 validation errors", 2, result.getErrors().size());
    }

    // -----------------------------------------------------------------------
    // Role-based access control
    // -----------------------------------------------------------------------

    @Test
    public void workerRole_rejected() {
        Result<Task> result = useCase.execute("org1", "My Task", "desc", "GRAB_ORDER", 1, "zone1",
                WINDOW_START, WINDOW_END, "user1", "WORKER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role"));
    }

    @Test
    public void complianceRole_rejected() {
        Result<Task> result = useCase.execute("org1", "My Task", "desc", "GRAB_ORDER", 1, "zone1",
                WINDOW_START, WINDOW_END, "user1", "COMPLIANCE_REVIEWER");
        assertFalse(result.isSuccess());
    }

    @Test
    public void adminRole_allowed() {
        Result<Task> result = useCase.execute("org1", "My Task", "desc", "GRAB_ORDER", 1, "zone1",
                WINDOW_START, WINDOW_END, "user1", "ADMIN");
        assertTrue(result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Content moderation tests
    // -----------------------------------------------------------------------

    @Test
    public void zeroToleranceContent_rejected() {
        Result<Task> result = useCaseWithScan.execute(
                "org1", "Task with badword in title", null,
                "GRAB_ORDER", 5, "zone1", WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("prohibited"));
    }

    @Test
    public void flaggedContent_rejectedByDefault() {
        Result<Task> result = useCaseWithScan.execute(
                "org1", "Task with flaggedword in title", null,
                "GRAB_ORDER", 5, "zone1", WINDOW_START, WINDOW_END, "user1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().startsWith("CONTENT_FLAGGED:"));
    }

    @Test
    public void flaggedContent_approvedWhenContentApproved() {
        Result<Task> result = useCaseWithScan.execute(
                "org1", "Task with flaggedword in title", null,
                "GRAB_ORDER", 5, "zone1", WINDOW_START, WINDOW_END, "user1", "DISPATCHER",
                true /* contentApproved */);
        assertTrue(result.isSuccess());
        assertEquals("OPEN", result.getData().status);
    }

    @Test
    public void nullOrgId_failure() {
        long now = System.currentTimeMillis();
        Result<Task> result = useCase.execute(null, "Valid Title", "desc",
            "GRAB_ORDER", 5, "zone1", now, now + 3_600_000L, "creator1", "DISPATCHER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("organisation"));
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubSensitiveWordRepository implements SensitiveWordRepository {
        List<String> zeroTolerance = new ArrayList<>();
        List<String> allWords = new ArrayList<>();

        @Override public List<String> getZeroToleranceWords() { return zeroTolerance; }
        @Override public List<String> getAllWords() { return allWords; }
        @Override public void addWord(String word, boolean isZeroTolerance) {
            if (isZeroTolerance) zeroTolerance.add(word);
            else allWords.add(word);
        }
        @Override public void removeWord(String id) {}
    }

    private static class StubTaskRepository implements TaskRepository {
        final Map<String, Task> tasks = new HashMap<>();
        private final Set<String> acceptances = new HashSet<>();

        @Override
        public Task getByIdScoped(String id, String orgId) { return tasks.get(id); }

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
            acceptances.add(claimedTask.id + ":" + workerId);
            tasks.put(claimedTask.id, claimedTask);
        }

        @Override
        public void claimTaskWithSideEffects(String acceptanceId, Task claimedTask, String workerId,
                long acceptedAt, int workloadDelta, String orgId, AuditLogEntry auditEntry) {
            acceptances.add(claimedTask.id + ":" + workerId);
            tasks.put(claimedTask.id, claimedTask);
        }

        @Override
        public void completeTaskWithSideEffects(Task completed, String workerId, int workloadDelta,
                String orgId, ReputationEvent reputationEvent, double newRepScore) {
            tasks.put(completed.id, completed);
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

    private static class StubZoneRepository implements ZoneRepository {
        private final Map<String, Zone> zones = new HashMap<>();

        @Override
        public Zone getByIdScoped(String id, String orgId) {
            Zone z = zones.get(id);
            if (z != null && z.orgId != null && !z.orgId.equals(orgId)) return null;
            return z;
        }

        @Override
        public void insert(Zone zone) { zones.put(zone.id, zone); }

        @Override
        public void update(Zone zone) { zones.put(zone.id, zone); }

        @Override
        public List<Zone> getZones(String orgId) {
            List<Zone> result = new ArrayList<>();
            for (Zone z : zones.values()) if (z.orgId.equals(orgId)) result.add(z);
            return result;
        }

        @Override
        public LiveData<List<Zone>> getZonesLive(String orgId) { return null; }
    }
}
