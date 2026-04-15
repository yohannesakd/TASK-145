package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.core.domain.usecase.AcceptTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CompleteTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CreateTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ScanContentUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.entity.WorkerEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ZoneEntity;
import com.roadrunner.dispatch.infrastructure.repository.AuditLogRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.SensitiveWordRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.TaskRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.WorkerRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ZoneRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * End-to-end instrumented test for the dispatch workflow:
 * Create → Claim/Assign → Complete, including role and org-boundary checks.
 */
@RunWith(AndroidJUnit4.class)
public class DispatchFlowIntegrationTest {

    private AppDatabase db;
    private TaskRepositoryImpl taskRepo;
    private WorkerRepositoryImpl workerRepo;
    private CreateTaskUseCase createTaskUseCase;
    private AcceptTaskUseCase acceptTaskUseCase;
    private CompleteTaskUseCase completeTaskUseCase;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        taskRepo = new TaskRepositoryImpl(db, db.taskDao(), db.taskAcceptanceDao());
        workerRepo = new WorkerRepositoryImpl(db.workerDao(), db.reputationEventDao());
        ZoneRepositoryImpl zoneRepo = new ZoneRepositoryImpl(db.zoneDao());
        AuditLogRepositoryImpl auditRepo = new AuditLogRepositoryImpl(db.auditLogDao());
        SensitiveWordRepositoryImpl sensitiveWordRepo =
                new SensitiveWordRepositoryImpl(db.sensitiveWordDao());

        ScanContentUseCase scanContent = new ScanContentUseCase(sensitiveWordRepo);
        createTaskUseCase = new CreateTaskUseCase(taskRepo, zoneRepo, scanContent);
        acceptTaskUseCase = new AcceptTaskUseCase(taskRepo, workerRepo, auditRepo);
        completeTaskUseCase = new CompleteTaskUseCase(taskRepo, workerRepo);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private void seedZone() {
        db.zoneDao().insert(new ZoneEntity("zone1", "org1", "Zone A", 5, "Primary zone"));
    }

    private void seedWorker(String id, String userId) {
        db.workerDao().insert(new WorkerEntity(
                id, userId, "org1", "Worker " + id, "AVAILABLE",
                0, 3.0, "zone1", NOW, NOW));
    }

    // -----------------------------------------------------------------------
    // Full dispatch flow
    // -----------------------------------------------------------------------

    @Test
    public void fullFlow_createClaimComplete() {
        seedZone();
        seedWorker("w1", "u1");

        // Step 1: Dispatcher creates task
        Result<Task> createResult = createTaskUseCase.execute(
                "org1", "Deliver package", "Deliver to 123 Main St",
                "GRAB_ORDER", 5, "zone1", NOW, NOW + 3600000, "dispatcher1",
                "DISPATCHER", false);
        assertTrue("Task creation should succeed: " + createResult.getFirstError(),
                createResult.isSuccess());
        Task created = createResult.getData();
        assertEquals("OPEN", created.status);

        // Step 2: Worker claims task (actorRole = WORKER)
        Result<Task> claimResult = acceptTaskUseCase.execute(
                created.id, "w1", "WORKER", "org1");
        assertTrue("Task claim should succeed: " + claimResult.getFirstError(),
                claimResult.isSuccess());
        Task claimed = claimResult.getData();
        assertEquals("ASSIGNED", claimed.status);

        // Verify worker workload incremented
        Worker worker = workerRepo.getByIdScoped("w1", "org1");
        assertEquals(1, worker.currentWorkload);

        // Step 3: Worker completes task
        Result<Task> completeResult = completeTaskUseCase.execute(
                created.id, "w1", "WORKER", "org1");
        assertTrue("Task completion should succeed: " + completeResult.getFirstError(),
                completeResult.isSuccess());
        Task completed = completeResult.getData();
        assertEquals("COMPLETED", completed.status);

        // Verify workload decremented
        Worker afterComplete = workerRepo.getByIdScoped("w1", "org1");
        assertEquals(0, afterComplete.currentWorkload);
    }

    // -----------------------------------------------------------------------
    // Role checks
    // -----------------------------------------------------------------------

    @Test
    public void createTask_workerRole_rejected() {
        seedZone();
        Result<Task> result = createTaskUseCase.execute(
                "org1", "Bad task", "desc", "GRAB_ORDER", 5, "zone1",
                NOW, NOW + 3600000, "worker1", "WORKER", false);
        assertFalse("Worker should not create tasks", result.isSuccess());
    }

    @Test
    public void acceptTask_adminRole_canAssign() {
        seedZone();
        seedWorker("w1", "u1");
        Result<Task> created = createTaskUseCase.execute(
                "org1", "Task", "desc", "ASSIGNED", 5, "zone1",
                NOW, NOW + 3600000, "disp1", "DISPATCHER", false);
        assertTrue(created.isSuccess());

        // Dispatcher can assign (different code path from WORKER claim)
        Result<Task> result = acceptTaskUseCase.execute(
                created.getData().id, "w1", "DISPATCHER", "org1");
        assertTrue("Dispatcher should be able to assign tasks", result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Org boundary checks
    // -----------------------------------------------------------------------

    @Test
    public void acceptTask_crossOrg_rejected() {
        seedZone();
        seedWorker("w1", "u1");
        Result<Task> created = createTaskUseCase.execute(
                "org1", "Task", "desc", "GRAB_ORDER", 5, "zone1",
                NOW, NOW + 3600000, "disp1", "DISPATCHER", false);
        assertTrue(created.isSuccess());

        // Worker from different org
        db.workerDao().insert(new WorkerEntity(
                "w2", "u2", "org2", "Foreign Worker", "AVAILABLE",
                0, 3.0, "zone1", NOW, NOW));

        Result<Task> result = acceptTaskUseCase.execute(
                created.getData().id, "w2", "WORKER", "org2");
        assertFalse("Cross-org claim should fail", result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Duplicate prevention
    // -----------------------------------------------------------------------

    @Test
    public void acceptTask_alreadyClaimed_rejected() {
        seedZone();
        seedWorker("w1", "u1");
        seedWorker("w2", "u2");

        Result<Task> created = createTaskUseCase.execute(
                "org1", "Task", "desc", "GRAB_ORDER", 5, "zone1",
                NOW, NOW + 3600000, "disp1", "DISPATCHER", false);
        assertTrue(created.isSuccess());

        // First claim succeeds
        Result<Task> first = acceptTaskUseCase.execute(
                created.getData().id, "w1", "WORKER", "org1");
        assertTrue(first.isSuccess());

        // Second claim fails
        Result<Task> second = acceptTaskUseCase.execute(
                created.getData().id, "w2", "WORKER", "org1");
        assertFalse("Double-claim should fail", second.isSuccess());
    }
}
