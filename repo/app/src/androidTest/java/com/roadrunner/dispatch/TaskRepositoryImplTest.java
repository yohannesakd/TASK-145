package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.ReputationEventDao;
import com.roadrunner.dispatch.infrastructure.db.dao.TaskAcceptanceDao;
import com.roadrunner.dispatch.infrastructure.db.dao.TaskDao;
import com.roadrunner.dispatch.infrastructure.db.dao.WorkerDao;
import com.roadrunner.dispatch.infrastructure.db.entity.ReputationEventEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.TaskAcceptanceEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.TaskEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.WorkerEntity;
import com.roadrunner.dispatch.infrastructure.repository.TaskRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Room-backed integration tests for {@link TaskRepositoryImpl}.
 * Verifies transactional atomicity of claimTaskWithSideEffects and
 * completeTaskWithSideEffects against a real in-memory Room database.
 */
@RunWith(AndroidJUnit4.class)
public class TaskRepositoryImplTest {

    private AppDatabase db;
    private TaskDao taskDao;
    private TaskAcceptanceDao acceptanceDao;
    private WorkerDao workerDao;
    private ReputationEventDao reputationEventDao;
    private TaskRepositoryImpl repo;

    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        taskDao = db.taskDao();
        acceptanceDao = db.taskAcceptanceDao();
        workerDao = db.workerDao();
        reputationEventDao = db.reputationEventDao();

        repo = new TaskRepositoryImpl(db, taskDao, acceptanceDao);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void insertOpenTask(String id, String orgId) {
        TaskEntity task = new TaskEntity(id, orgId, "Test Task", "desc",
                "OPEN", "GRAB_ORDER", 5, "zone1",
                NOW, NOW + 3_600_000L, null, "creator1", NOW, NOW);
        taskDao.insert(task);
    }

    private void insertAssignedTask(String id, String orgId, String workerId) {
        TaskEntity task = new TaskEntity(id, orgId, "Assigned Task", "desc",
                "ASSIGNED", "GRAB_ORDER", 5, "zone1",
                NOW, NOW + 3_600_000L, workerId, "creator1", NOW, NOW);
        taskDao.insert(task);
    }

    private void insertWorker(String id, String orgId, int workload, double rep) {
        WorkerEntity worker = new WorkerEntity(id, "u-" + id, orgId, "Worker " + id,
                "AVAILABLE", workload, rep, "zone1", NOW, NOW);
        workerDao.insert(worker);
    }

    // -----------------------------------------------------------------------
    // claimTaskWithSideEffects tests
    // -----------------------------------------------------------------------

    @Test
    public void claimTaskWithSideEffects_allWritesAtomic() {
        insertOpenTask("t1", "org1");
        insertWorker("w1", "org1", 3, 3.0);

        Task claimedTask = new Task("t1", "org1", "Test Task", "desc",
                "ASSIGNED", "GRAB_ORDER", "5", "zone1",
                NOW, NOW + 3_600_000L, "w1", "creator1");
        AuditLogEntry audit = new AuditLogEntry("a1", "org1", "w1",
                "TASK_CLAIMED", "TASK", "t1", "{}", null, NOW);

        repo.claimTaskWithSideEffects("acc1", claimedTask, "w1", NOW, 1, "org1", audit);

        // Task status updated to ASSIGNED
        TaskEntity updatedTask = taskDao.findByIdAndOrg("t1", "org1");
        assertEquals("ASSIGNED", updatedTask.status);
        assertEquals("w1", updatedTask.assignedWorkerId);

        // Acceptance record inserted
        TaskAcceptanceEntity acceptance = acceptanceDao.findByTaskAndWorker("t1", "w1");
        assertNotNull(acceptance);
        assertEquals("acc1", acceptance.id);

        // Worker workload adjusted (+1)
        WorkerEntity updatedWorker = workerDao.findByIdAndOrg("w1", "org1");
        assertEquals(4, updatedWorker.currentWorkload);

        // All writes committed atomically — task, acceptance, and workload all present
        assertNotNull(updatedTask);
    }

    @Test
    public void claimTaskWithSideEffects_taskAlreadyClaimed_rollsBack() {
        // Insert a task that is already ASSIGNED (not OPEN)
        insertAssignedTask("t1", "org1", "w-other");
        insertWorker("w1", "org1", 3, 3.0);

        Task claimedTask = new Task("t1", "org1", "Assigned Task", "desc",
                "ASSIGNED", "GRAB_ORDER", "5", "zone1",
                NOW, NOW + 3_600_000L, "w1", "creator1");
        AuditLogEntry audit = new AuditLogEntry("a1", "org1", "w1",
                "TASK_CLAIMED", "TASK", "t1", "{}", null, NOW);

        try {
            repo.claimTaskWithSideEffects("acc1", claimedTask, "w1", NOW, 1, "org1", audit);
            fail("Expected IllegalStateException for already-claimed task");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("already claimed"));
        }

        // Verify rollback: no acceptance inserted, workload unchanged
        TaskAcceptanceEntity acceptance = acceptanceDao.findByTaskAndWorker("t1", "w1");
        assertNull(acceptance);

        WorkerEntity worker = workerDao.findByIdAndOrg("w1", "org1");
        assertEquals(3, worker.currentWorkload); // unchanged
    }

    @Test
    public void claimTaskWithSideEffects_duplicateAcceptance_rollsBack() {
        insertOpenTask("t1", "org1");
        insertWorker("w1", "org1", 3, 3.0);

        // Pre-insert an acceptance record for the same task+worker
        TaskAcceptanceEntity existing = new TaskAcceptanceEntity(
                "acc-existing", "t1", "w1", NOW, "ACCEPTED");
        acceptanceDao.insert(existing);

        Task claimedTask = new Task("t1", "org1", "Test Task", "desc",
                "ASSIGNED", "GRAB_ORDER", "5", "zone1",
                NOW, NOW + 3_600_000L, "w1", "creator1");
        AuditLogEntry audit = new AuditLogEntry("a1", "org1", "w1",
                "TASK_CLAIMED", "TASK", "t1", "{}", null, NOW);

        try {
            repo.claimTaskWithSideEffects("acc1", claimedTask, "w1", NOW, 1, "org1", audit);
            fail("Expected exception on duplicate acceptance");
        } catch (Exception e) {
            // UNIQUE constraint violation or IllegalStateException — both acceptable
        }

        // Workload should still be 3 (rollback)
        WorkerEntity worker = workerDao.findByIdAndOrg("w1", "org1");
        assertEquals(3, worker.currentWorkload);
    }

    // -----------------------------------------------------------------------
    // completeTaskWithSideEffects tests
    // -----------------------------------------------------------------------

    @Test
    public void completeTaskWithSideEffects_allWritesAtomic() {
        insertAssignedTask("t1", "org1", "w1");
        insertWorker("w1", "org1", 4, 3.0);

        Task completedTask = new Task("t1", "org1", "Assigned Task", "desc",
                "COMPLETED", "GRAB_ORDER", "5", "zone1",
                NOW, NOW + 3_600_000L, "w1", "creator1");
        ReputationEvent repEvent = new ReputationEvent("rep1", "w1",
                "TASK_COMPLETED", 0.5, "t1", "Good work");

        repo.completeTaskWithSideEffects(completedTask, "w1", -1, "org1", repEvent, 3.5);

        // Task status updated to COMPLETED
        TaskEntity updatedTask = taskDao.findByIdAndOrg("t1", "org1");
        assertEquals("COMPLETED", updatedTask.status);

        // Worker workload decremented (4 + (-1) = 3)
        WorkerEntity updatedWorker = workerDao.findByIdAndOrg("w1", "org1");
        assertEquals(3, updatedWorker.currentWorkload);

        // Reputation score updated
        assertEquals(3.5, updatedWorker.reputationScore, 0.001);

        // Reputation event inserted
        List<ReputationEventEntity> events = reputationEventDao.getRecentEvents("w1", 10);
        assertEquals(1, events.size());
        assertEquals("TASK_COMPLETED", events.get(0).eventType);
        assertEquals(0.5, events.get(0).delta, 0.001);
    }

    @Test
    public void completeTaskWithSideEffects_workloadAndRepConsistent() {
        insertAssignedTask("t1", "org1", "w1");
        insertWorker("w1", "org1", 2, 2.0);

        Task completedTask = new Task("t1", "org1", "Assigned Task", "desc",
                "COMPLETED", "GRAB_ORDER", "5", "zone1",
                NOW, NOW + 3_600_000L, "w1", "creator1");
        ReputationEvent repEvent = new ReputationEvent("rep1", "w1",
                "TASK_COMPLETED", 1.0, "t1", "Excellent");

        repo.completeTaskWithSideEffects(completedTask, "w1", -1, "org1", repEvent, 4.0);

        WorkerEntity worker = workerDao.findByIdAndOrg("w1", "org1");
        assertEquals(1, worker.currentWorkload);   // 2 + (-1) = 1
        assertEquals(4.0, worker.reputationScore, 0.001);
    }

    // -----------------------------------------------------------------------
    // Basic repository CRUD tests
    // -----------------------------------------------------------------------

    @Test
    public void insert_and_getById() {
        Task task = new Task("t1", "org1", "Title", "desc",
                "OPEN", "GRAB_ORDER", "5", "zone1",
                NOW, NOW + 3_600_000L, null, "creator1");
        repo.insert(task);

        Task fetched = repo.getByIdScoped("t1", "org1");
        assertNotNull(fetched);
        assertEquals("t1", fetched.id);
        assertEquals("OPEN", fetched.status);
    }

    @Test
    public void getByIdScoped_wrongOrg_returnsNull() {
        insertOpenTask("t1", "org1");

        Task fetched = repo.getByIdScoped("t1", "org-other");
        assertNull(fetched);
    }

    @Test
    public void getByIdScoped_correctOrg_returnsTask() {
        insertOpenTask("t1", "org1");

        Task fetched = repo.getByIdScoped("t1", "org1");
        assertNotNull(fetched);
        assertEquals("t1", fetched.id);
    }
}
