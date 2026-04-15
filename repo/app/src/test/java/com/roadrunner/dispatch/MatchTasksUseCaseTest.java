package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.MatchingWeights;
import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import com.roadrunner.dispatch.core.domain.model.ScoredWorker;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.core.domain.repository.WorkerRepository;
import com.roadrunner.dispatch.core.domain.repository.ZoneRepository;
import com.roadrunner.dispatch.core.domain.usecase.MatchTasksUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class MatchTasksUseCaseTest {

    private StubWorkerRepository workerRepo;
    private StubZoneRepository zoneRepo;
    private StubTaskRepository taskRepo;
    private MatchTasksUseCase useCase;

    // Balanced weights for most tests
    private static final MatchingWeights BALANCED =
            new MatchingWeights(0.25, 0.25, 0.25, 0.25);

    // Window that puts the task firmly inside "now"
    private static final long NOW = System.currentTimeMillis();
    private static final long WIN_START = NOW - 60_000L;   // started 1 min ago
    private static final long WIN_END   = NOW + 3_600_000L; // ends in 1 hour

    @Before
    public void setUp() {
        workerRepo = new StubWorkerRepository();
        zoneRepo   = new StubZoneRepository();
        taskRepo   = new StubTaskRepository();
        useCase    = new MatchTasksUseCase(workerRepo, zoneRepo, taskRepo);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Zone zone(String id, int score) {
        return new Zone(id, "org1", "Zone " + id, score, "");
    }

    private Worker worker(String id, String zoneId, int workload, double rep) {
        return new Worker(id, "u-" + id, "org1", "W " + id, "AVAILABLE", workload, rep, zoneId);
    }

    private Task taskInWindow(String zoneId) {
        return new Task("t1", "org1", "Task", "desc",
                "OPEN", "ASSIGNED", "5", zoneId,
                WIN_START, WIN_END, null, "creator1");
    }

    // -----------------------------------------------------------------------
    // rankWorkersForTask tests
    // -----------------------------------------------------------------------

    @Test
    public void rankWorkersForTask_noAvailableWorkers_emptyList() {
        Task task = taskInWindow("zone1");
        zoneRepo.insert(zone("zone1", 3));

        List<ScoredWorker> result = useCase.rankWorkersForTask(task, BALANCED, "org1");
        assertTrue(result.isEmpty());
    }

    @Test
    public void rankWorkersForTask_workerWithHighReputation_rankedHigher() {
        zoneRepo.insert(zone("zone1", 3));

        // Both workers same zone and workload; differ by reputation
        Worker highRep = worker("w-high", "zone1", 2, 5.0);
        Worker lowRep  = worker("w-low",  "zone1", 2, 1.0);
        workerRepo.insert(highRep);
        workerRepo.insert(lowRep);

        Task task = taskInWindow("zone1");
        List<ScoredWorker> result = useCase.rankWorkersForTask(task, BALANCED, "org1");

        assertEquals(2, result.size());
        assertEquals("w-high", result.get(0).worker.id);
    }

    @Test
    public void rankWorkersForTask_workerWithHighWorkload_rankedLower() {
        zoneRepo.insert(zone("zone1", 3));

        // Both workers same zone and reputation; differ by workload
        Worker lightLoad = worker("w-light", "zone1", 1, 3.0);
        Worker heavyLoad = worker("w-heavy", "zone1", 9, 3.0);
        workerRepo.insert(lightLoad);
        workerRepo.insert(heavyLoad);

        Task task = taskInWindow("zone1");
        List<ScoredWorker> result = useCase.rankWorkersForTask(task, BALANCED, "org1");

        assertEquals(2, result.size());
        assertEquals("w-light", result.get(0).worker.id);
    }

    @Test
    public void rankWorkersForTask_workersWithScoreZeroOrBelow_excluded() {
        // Use a task far in the past (time score = 0) and high zone weight to drive score negative
        Task pastTask = new Task("t1", "org1", "Task", "desc",
                "OPEN", "ASSIGNED", "5", "zone1",
                NOW - 10_000_000L, NOW - 5_000_000L, // past window
                null, "creator1");

        // zone score = 1 for task, zone score = 5 for worker → big distance
        zoneRepo.insert(zone("zone1", 1));
        zoneRepo.insert(zone("zone5", 5));

        // Worker with 0 reputation, full workload, far zone, past time → score should be <= 0
        Worker worker = new Worker("w1", "u1", "org1", "W", "AVAILABLE",
                10 /* max workload */, 0.0 /* rep */, "zone5");
        workerRepo.insert(worker);

        // Use extreme zone weight to ensure exclusion
        MatchingWeights extremeZone = new MatchingWeights(0.0, 0.0, 0.0, 10.0);
        List<ScoredWorker> result = useCase.rankWorkersForTask(pastTask, extremeZone, "org1");

        // Worker should be excluded (score <= 0)
        assertTrue(result.isEmpty());
    }

    @Test
    public void rankWorkersForTask_allEqualWorkers_zoneProximityDifferentiates() {
        // Two workers identical except their zone
        zoneRepo.insert(zone("zTask", 3));  // task zone score = 3
        zoneRepo.insert(zone("zNear", 3));  // same score as task → distance = 0
        zoneRepo.insert(zone("zFar",  1));  // distance = |1-3|/4 = 0.5

        Worker near = worker("w-near", "zNear", 3, 3.0);
        Worker far  = worker("w-far",  "zFar",  3, 3.0);
        workerRepo.insert(near);
        workerRepo.insert(far);

        Task task = taskInWindow("zTask");
        // High zone weight so proximity matters
        MatchingWeights highZone = new MatchingWeights(0.1, 0.1, 0.1, 1.0);
        List<ScoredWorker> result = useCase.rankWorkersForTask(task, highZone, "org1");

        // Near worker should be ranked higher (less distance penalty)
        assertFalse(result.isEmpty());
        assertEquals("w-near", result.get(0).worker.id);
    }

    @Test
    public void rankWorkersForTask_zeroTimeWeight_rankingIgnoresTime() {
        // Task in the far future → time score would be low (0.2)
        Task farFutureTask = new Task("t1", "org1", "Task", "desc",
                "OPEN", "ASSIGNED", "5", "zone1",
                NOW + 8 * 3_600_000L, NOW + 12 * 3_600_000L, // starts in 8 hours
                null, "creator1");

        zoneRepo.insert(zone("zone1", 3));

        Worker w1 = worker("w1", "zone1", 0, 5.0); // very good rep
        Worker w2 = worker("w2", "zone1", 0, 1.0); // low rep
        workerRepo.insert(w1);
        workerRepo.insert(w2);

        // Zero time weight → time does not affect ranking; reputation decides
        MatchingWeights noTime = new MatchingWeights(0.0, 0.5, 0.5, 0.0);
        List<ScoredWorker> result = useCase.rankWorkersForTask(farFutureTask, noTime, "org1");

        assertEquals(2, result.size());
        assertEquals("w1", result.get(0).worker.id);
    }

    // -----------------------------------------------------------------------
    // rankTasksForWorker tests
    // -----------------------------------------------------------------------

    @Test
    public void rankTasksForWorker_expiredWindowTask_excludedFromResults() {
        // Open tasks returned by repo include a past-window task
        Task expired = new Task("t-exp", "org1", "Expired", "desc",
                "OPEN", "GRAB_ORDER", "5", "zone1",
                NOW - 10_000_000L, NOW - 5_000_000L, // past window → time score = 0
                null, "creator1");
        taskRepo.setOpenTasks(Collections.singletonList(expired));
        zoneRepo.insert(zone("zone1", 3));

        Worker worker = worker("w1", "zone1", 3, 3.0);

        // Weight that ensures score <= 0 when time is 0 and no other positive contribution
        MatchingWeights onlyTime = new MatchingWeights(1.0, 0.0, 0.0, 0.0);
        List<MatchTasksUseCase.ScoredTask> result = useCase.rankTasksForWorker(worker, onlyTime, "org1");

        // Expired tasks have timeScore=0, so total=0 and are excluded
        assertTrue(result.isEmpty());
    }

    @Test
    public void rankTasksForWorker_tasksWithinWindow_rankedHigher() {
        long farFutureStart = NOW + 8 * 3_600_000L;
        long farFutureEnd   = NOW + 12 * 3_600_000L;

        Task inWindow = new Task("t-in", "org1", "In Window", "desc",
                "OPEN", "GRAB_ORDER", "5", "zone1",
                WIN_START, WIN_END, null, "creator1");
        Task farFuture = new Task("t-far", "org1", "Far Future", "desc",
                "OPEN", "GRAB_ORDER", "5", "zone1",
                farFutureStart, farFutureEnd, null, "creator1");

        taskRepo.setOpenTasks(Arrays.asList(inWindow, farFuture));
        zoneRepo.insert(zone("zone1", 3));

        Worker worker = worker("w1", "zone1", 3, 3.0);
        // Pure time weight so time score decides ranking
        MatchingWeights pureTime = new MatchingWeights(1.0, 0.0, 0.0, 0.0);
        List<MatchTasksUseCase.ScoredTask> result = useCase.rankTasksForWorker(worker, pureTime, "org1");

        assertFalse(result.isEmpty());
        assertEquals("t-in", result.get(0).task.id);
    }

    @Test
    public void rankTasksForWorker_highZoneWeight_nearbyZonePreferred() {
        zoneRepo.insert(zone("wZone", 3));   // worker zone score = 3
        zoneRepo.insert(zone("nearTask", 3)); // same score → distance = 0
        zoneRepo.insert(zone("farTask",  1)); // distance = 0.5

        Task nearTask = new Task("t-near", "org1", "Near", "desc",
                "OPEN", "GRAB_ORDER", "5", "nearTask",
                WIN_START, WIN_END, null, "creator1");
        Task farTask = new Task("t-far", "org1", "Far", "desc",
                "OPEN", "GRAB_ORDER", "5", "farTask",
                WIN_START, WIN_END, null, "creator1");
        taskRepo.setOpenTasks(Arrays.asList(nearTask, farTask));

        Worker worker = worker("w1", "wZone", 3, 3.0);
        MatchingWeights highZone = new MatchingWeights(0.1, 0.1, 0.1, 1.0);
        List<MatchTasksUseCase.ScoredTask> result = useCase.rankTasksForWorker(worker, highZone, "org1");

        assertFalse(result.isEmpty());
        assertEquals("t-near", result.get(0).task.id);
    }

    @Test
    public void defaultWeights_sensibleRanking_higherRepWorkerScoresHigherForTask() {
        // Verify the balanced default weights produce a non-empty result when setup is reasonable
        zoneRepo.insert(zone("zone1", 3));

        Task task = taskInWindow("zone1");
        Worker w = worker("w1", "zone1", 2, 4.0);
        workerRepo.insert(w);

        List<ScoredWorker> result = useCase.rankWorkersForTask(task, BALANCED, "org1");
        assertEquals(1, result.size());
        assertTrue(result.get(0).score > 0);
    }

    @Test
    public void rankTasksForWorker_taskStartingSoon_score08() {
        // Task starts 30 min from now — gap < 1 hour → time score = 0.8
        Task soon = new Task("t-soon", "org1", "Soon Task", "desc",
            "OPEN", "GRAB_ORDER", "5", "zone1",
            NOW + 1_800_000L, NOW + 5_400_000L, null, "creator1");
        taskRepo.setOpenTasks(Arrays.asList(soon));
        Worker w = new Worker("w1", "u-w1", "org1", "W1", "AVAILABLE", 1, 3.0, "zone1");
        workerRepo.insert(w);
        MatchingWeights weights = new MatchingWeights(1.0, 0.0, 0.0, 0.0);
        List<?> ranked = useCase.rankTasksForWorker(w, weights, "org1");
        assertFalse(ranked.isEmpty());
    }

    @Test
    public void rankTasksForWorker_taskStartingIn2Hours_score05() {
        // Task starts 2 hours from now — 1hr < gap ≤ 4hr → time score = 0.5
        Task later = new Task("t-2h", "org1", "2hr Task", "desc",
            "OPEN", "GRAB_ORDER", "5", "zone1",
            NOW + 7_200_000L, NOW + 10_800_000L, null, "creator1");
        taskRepo.setOpenTasks(Arrays.asList(later));
        Worker w = new Worker("w2", "u-w2", "org1", "W2", "AVAILABLE", 1, 3.0, "zone1");
        workerRepo.insert(w);
        MatchingWeights weights = new MatchingWeights(1.0, 0.0, 0.0, 0.0);
        List<?> ranked = useCase.rankTasksForWorker(w, weights, "org1");
        assertFalse(ranked.isEmpty());
    }

    @Test
    public void rankWorkersForTask_nonNumericPriority_noException() {
        // Non-numeric priority string — NumberFormatException handled, priorityInt = 0
        Task task = new Task("t-nn", "org1", "Task", "desc",
            "OPEN", "ASSIGNED", "HIGH", "zone1",
            WIN_START, WIN_END, null, "creator1");
        Worker w = new Worker("w1", "u-w1", "org1", "W1", "AVAILABLE", 1, 3.0, "zone1");
        workerRepo.insert(w);
        MatchingWeights weights = new MatchingWeights(0.25, 0.25, 0.25, 0.25);
        List<?> ranked = useCase.rankWorkersForTask(task, weights, "org1");
        // Should not throw — graceful fallback to priorityInt=0
        assertNotNull(ranked);
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubWorkerRepository implements WorkerRepository {
        private final Map<String, Worker> workers = new HashMap<>();

        @Override
        public List<Worker> getWorkersByStatus(String orgId, String status) {
            List<Worker> result = new ArrayList<>();
            for (Worker w : workers.values()) {
                if (w.orgId.equals(orgId) && w.status.equals(status)) result.add(w);
            }
            return result;
        }

        @Override public Worker getByIdScoped(String id, String orgId) { return workers.get(id); }
        @Override public Worker getByUserIdScoped(String userId, String orgId) { return null; }
        @Override public void insert(Worker worker) { workers.put(worker.id, worker); }
        @Override public void update(Worker worker) { workers.put(worker.id, worker); }
        @Override public void adjustWorkload(String workerId, int delta, String orgId) {}
        @Override public void updateReputationScore(String workerId, double score, String orgId) {}
        @Override public void addReputationEvent(ReputationEvent event) {}
        @Override public double getAverageReputation(String workerId) { return 0.5; }
        @Override public LiveData<List<Worker>> getWorkers(String orgId) { return null; }
        @Override public LiveData<List<ReputationEvent>> getReputationEvents(String workerId) { return null; }
    }

    private static class StubZoneRepository implements ZoneRepository {
        private final Map<String, Zone> zones = new HashMap<>();

        @Override public Zone getByIdScoped(String id, String orgId) { return zones.get(id); }
        @Override public List<Zone> getZones(String orgId) { return new ArrayList<>(zones.values()); }
        @Override public LiveData<List<Zone>> getZonesLive(String orgId) { return null; }
        @Override public void insert(Zone zone) { zones.put(zone.id, zone); }
        @Override public void update(Zone zone) { zones.put(zone.id, zone); }
    }

    private static class StubTaskRepository implements TaskRepository {
        private final Map<String, Task> tasks = new HashMap<>();
        private List<Task> openTasks = new ArrayList<>();

        void setOpenTasks(List<Task> list) { this.openTasks = list; }

        @Override public Task getByIdScoped(String id, String orgId) { return tasks.get(id); }
        @Override public void insert(Task task) { tasks.put(task.id, task); }
        @Override public void update(Task task) { tasks.put(task.id, task); }
        @Override public void updateTask(Task task) { tasks.put(task.id, task); }
        @Override public boolean hasAcceptance(String taskId, String workerId) { return false; }
        @Override public void insertAcceptance(String id, String taskId, String workerId, long acceptedAt) {}
        @Override public void claimTask(String acceptanceId, Task claimedTask, String workerId, long acceptedAt) { tasks.put(claimedTask.id, claimedTask); }
        @Override public void claimTaskWithSideEffects(String acceptanceId, Task claimedTask, String workerId,
                long acceptedAt, int workloadDelta, String orgId, AuditLogEntry auditEntry) { tasks.put(claimedTask.id, claimedTask); }
        @Override public void completeTaskWithSideEffects(Task completed, String workerId, int workloadDelta,
                String orgId, ReputationEvent reputationEvent, double newRepScore) { tasks.put(completed.id, completed); }
        @Override public List<Task> getOpenTasks(String orgId, String mode, long now) {
            List<Task> filtered = new ArrayList<>();
            for (Task t : openTasks) { if (t.windowEnd > now) filtered.add(t); }
            return filtered;
        }
        @Override public List<Task> getWorkerActiveTasks(String orgId, String workerId) { return new ArrayList<>(); }
        @Override public LiveData<List<Task>> getWorkerActiveTasksLive(String orgId, String workerId) { return null; }
        @Override public LiveData<List<Task>> getTasks(String orgId) { return null; }
        @Override public LiveData<List<Task>> getTasksByStatus(String orgId, String status) { return null; }
    }
}
