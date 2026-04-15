package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.ContentScanResult;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.usecase.CreateTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.MatchTasksUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ScanContentUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.entity.SensitiveWordEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.WorkerEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ZoneEntity;
import com.roadrunner.dispatch.infrastructure.repository.SensitiveWordRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.TaskRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.WorkerRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ZoneRepositoryImpl;
import com.roadrunner.dispatch.presentation.dispatch.tasklist.TaskListViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented integration tests for {@link TaskListViewModel} wired to real Room DB.
 * Exercises task creation, content scanning, ranked task loading, and my-tasks loading.
 */
@RunWith(AndroidJUnit4.class)
public class TaskListViewModelTest {

    private AppDatabase db;
    private TaskListViewModel viewModel;
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
        SensitiveWordRepositoryImpl sensitiveWordRepo =
                new SensitiveWordRepositoryImpl(db.sensitiveWordDao());

        CreateTaskUseCase createTask = new CreateTaskUseCase(taskRepo, zoneRepo,
                new ScanContentUseCase(sensitiveWordRepo));
        MatchTasksUseCase matchTasks = new MatchTasksUseCase(workerRepo, zoneRepo, taskRepo);
        ScanContentUseCase scanContent = new ScanContentUseCase(sensitiveWordRepo);

        viewModel = new TaskListViewModel(taskRepo, workerRepo, createTask, matchTasks, scanContent);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private void seedZone() {
        db.zoneDao().insert(new ZoneEntity("zone1", "org1", "Zone A", 5, "Primary"));
    }

    private void seedWorker() {
        db.workerDao().insert(new WorkerEntity(
                "w1", "u1", "org1", "Alice", "AVAILABLE", 0, 3.0, "zone1", NOW, NOW));
    }

    // -----------------------------------------------------------------------
    // Task creation via ViewModel
    // -----------------------------------------------------------------------

    @Test
    public void createTask_validInput_postsCreatedTask() throws InterruptedException {
        seedZone();
        CountDownLatch latch = new CountDownLatch(1);
        final Task[] observed = {null};

        viewModel.getCreatedTask().observeForever(task -> {
            if (task != null) {
                observed[0] = task;
                latch.countDown();
            }
        });

        viewModel.createTask("org1", "Deliver package", "To 123 Main St",
                "GRAB_ORDER", 5, "zone1", NOW, NOW + 3600000, "disp1", "DISPATCHER");

        assertTrue("Task should be created within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertEquals("Deliver package", observed[0].title);
        assertEquals("OPEN", observed[0].status);
    }

    @Test
    public void createTask_workerRole_postsError() throws InterruptedException {
        seedZone();
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        viewModel.createTask("org1", "Bad task", "desc",
                "GRAB_ORDER", 5, "zone1", NOW, NOW + 3600000, "w1", "WORKER");

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    // -----------------------------------------------------------------------
    // Content scanning
    // -----------------------------------------------------------------------

    @Test
    public void scanContent_cleanText_returnsClean() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final ContentScanResult[] observed = {null};

        viewModel.getScanResult().observeForever(result -> {
            if (result != null) {
                observed[0] = result;
                latch.countDown();
            }
        });

        viewModel.scanContent("Normal task description with no issues");

        assertTrue("Scan should complete within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertEquals("CLEAN", observed[0].status);
    }

    @Test
    public void scanContent_withSensitiveWord_returnsFlagged() throws InterruptedException {
        db.sensitiveWordDao().insert(new SensitiveWordEntity("sw1", "badword", false, NOW));

        CountDownLatch latch = new CountDownLatch(1);
        final ContentScanResult[] observed = {null};

        viewModel.getScanResult().observeForever(result -> {
            if (result != null) {
                observed[0] = result;
                latch.countDown();
            }
        });

        viewModel.scanContent("This contains badword in the text");

        assertTrue("Scan should complete within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertEquals("FLAGGED", observed[0].status);
        assertTrue(observed[0].matchedTerms.contains("badword"));
    }

    // -----------------------------------------------------------------------
    // My tasks loading
    // -----------------------------------------------------------------------

    @Test
    public void loadMyTasks_noTasks_postsEmptyList() throws InterruptedException {
        seedWorker();
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getMyTasks().observeForever(tasks -> {
            if (tasks != null) latch.countDown();
        });

        viewModel.loadMyTasks("w1", "org1");

        assertTrue("My tasks should load within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    // -----------------------------------------------------------------------
    // LiveData accessors
    // -----------------------------------------------------------------------

    @Test
    public void getTasks_returnsLiveData() {
        assertNotNull(viewModel.getTasks("org1"));
    }

    @Test
    public void getTasksByStatus_returnsLiveData() {
        assertNotNull(viewModel.getTasksByStatus("org1", "OPEN"));
    }
}
