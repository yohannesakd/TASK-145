package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.MatchingWeights;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.usecase.AcceptTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CompleteTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.MatchTasksUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.entity.TaskEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.WorkerEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ZoneEntity;
import com.roadrunner.dispatch.infrastructure.repository.AuditLogRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.TaskRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.WorkerRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ZoneRepositoryImpl;
import com.roadrunner.dispatch.presentation.dispatch.taskdetail.TaskDetailViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented tests for {@link TaskDetailViewModel} wired to real Room DB.
 */
@RunWith(AndroidJUnit4.class)
public class TaskDetailViewModelTest {

    private AppDatabase db;
    private TaskDetailViewModel viewModel;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        TaskRepositoryImpl taskRepo = new TaskRepositoryImpl(db, db.taskDao(), db.taskAcceptanceDao());
        WorkerRepositoryImpl workerRepo = new WorkerRepositoryImpl(db.workerDao(), db.reputationEventDao());
        ZoneRepositoryImpl zoneRepo = new ZoneRepositoryImpl(db.zoneDao());

        AuditLogRepositoryImpl auditRepo = new AuditLogRepositoryImpl(db.auditLogDao());
        AcceptTaskUseCase acceptUseCase = new AcceptTaskUseCase(taskRepo, workerRepo, auditRepo);
        CompleteTaskUseCase completeUseCase = new CompleteTaskUseCase(taskRepo, workerRepo);
        MatchTasksUseCase matchUseCase = new MatchTasksUseCase(workerRepo, zoneRepo, taskRepo);

        viewModel = new TaskDetailViewModel(taskRepo, acceptUseCase, completeUseCase, matchUseCase);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private void seedZoneAndTask() {
        db.zoneDao().insert(new ZoneEntity("zone1", "org1", "Zone A", 5, "Primary"));
        db.taskDao().insert(new TaskEntity("t1", "org1", "Deliver package", "To main st",
                "OPEN", "GRAB_ORDER", 5, "zone1", NOW, NOW + 3600000, null, "disp1", NOW, NOW));
    }

    private void seedWorker() {
        db.workerDao().insert(new WorkerEntity(
                "w1", "u1", "org1", "Alice", "AVAILABLE", 0, 3.0, "zone1", NOW, NOW));
    }

    @Test
    public void loadTask_existingTask_postsTask() throws InterruptedException {
        seedZoneAndTask();
        CountDownLatch latch = new CountDownLatch(1);
        final Task[] observed = {null};

        viewModel.getTask().observeForever(task -> {
            if (task != null) {
                observed[0] = task;
                latch.countDown();
            }
        });

        viewModel.loadTask("t1", false, "org1",
                new MatchingWeights(0.25, 0.25, 0.25, 0.25));

        assertTrue("Task should load within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertEquals("Deliver package", observed[0].title);
    }

    @Test
    public void loadTask_nonexistent_postsError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        viewModel.loadTask("nonexistent", false, "org1",
                new MatchingWeights(0.25, 0.25, 0.25, 0.25));

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void acceptTask_grabOrder_postsActionResult() throws InterruptedException {
        seedZoneAndTask();
        seedWorker();

        // First load the task to set orgId
        CountDownLatch loadLatch = new CountDownLatch(1);
        viewModel.getTask().observeForever(t -> {
            if (t != null) loadLatch.countDown();
        });
        viewModel.loadTask("t1", false, "org1", null);
        assertTrue(loadLatch.await(5, TimeUnit.SECONDS));

        CountDownLatch latch = new CountDownLatch(1);
        final Task[] observed = {null};

        viewModel.getActionResult().observeForever(task -> {
            if (task != null) {
                observed[0] = task;
                latch.countDown();
            }
        });

        viewModel.acceptTask("t1", "w1", "WORKER");

        assertTrue("Accept should complete within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
    }

    @Test
    public void startTask_nonWorkerRole_postsError() throws InterruptedException {
        seedZoneAndTask();

        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        viewModel.startTask("t1", "w1", "DISPATCHER");

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void getTask_returnsLiveData() {
        assertNotNull(viewModel.getTask());
    }

    @Test
    public void getRankedWorkers_returnsLiveData() {
        assertNotNull(viewModel.getRankedWorkers());
    }

    @Test
    public void getActionResult_returnsLiveData() {
        assertNotNull(viewModel.getActionResult());
    }
}
