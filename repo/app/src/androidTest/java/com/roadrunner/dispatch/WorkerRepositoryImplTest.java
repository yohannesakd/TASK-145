package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.ReputationEventDao;
import com.roadrunner.dispatch.infrastructure.db.dao.WorkerDao;
import com.roadrunner.dispatch.infrastructure.repository.WorkerRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WorkerRepositoryImplTest {

    private AppDatabase db;
    private WorkerDao workerDao;
    private ReputationEventDao reputationEventDao;
    private WorkerRepositoryImpl repo;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        workerDao = db.workerDao();
        reputationEventDao = db.reputationEventDao();
        repo = new WorkerRepositoryImpl(workerDao, reputationEventDao);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -----------------------------------------------------------------------
    // Insert and query
    // -----------------------------------------------------------------------

    @Test
    public void insert_and_getByIdScoped() {
        Worker w = new Worker("w1", "u1", "org1", "Alice", "AVAILABLE", 0, 3.0, "zone1");
        repo.insert(w);
        Worker found = repo.getByIdScoped("w1", "org1");
        assertNotNull(found);
        assertEquals("Alice", found.name);
        assertEquals("AVAILABLE", found.status);
        assertEquals(3.0, found.reputationScore, 0.001);
    }

    @Test
    public void getByIdScoped_wrongOrg_returnsNull() {
        Worker w = new Worker("w1", "u1", "org1", "Bob", "AVAILABLE", 0, 3.0, "zone1");
        repo.insert(w);
        assertNull(repo.getByIdScoped("w1", "org2"));
    }

    @Test
    public void getByUserIdScoped_returnsWorker() {
        Worker w = new Worker("w1", "u1", "org1", "Carol", "AVAILABLE", 2, 3.5, "zone1");
        repo.insert(w);
        Worker found = repo.getByUserIdScoped("u1", "org1");
        assertNotNull(found);
        assertEquals("w1", found.id);
    }

    // -----------------------------------------------------------------------
    // Workload and reputation
    // -----------------------------------------------------------------------

    @Test
    public void adjustWorkload_incrementsCorrectly() {
        Worker w = new Worker("w1", "u1", "org1", "Dave", "AVAILABLE", 3, 3.0, "zone1");
        repo.insert(w);
        repo.adjustWorkload("w1", 1, "org1");
        Worker updated = repo.getByIdScoped("w1", "org1");
        assertNotNull(updated);
        assertEquals(4, updated.currentWorkload);
    }

    @Test
    public void updateReputationScore_persisted() {
        Worker w = new Worker("w1", "u1", "org1", "Eve", "AVAILABLE", 0, 3.0, "zone1");
        repo.insert(w);
        repo.updateReputationScore("w1", 4.5, "org1");
        Worker updated = repo.getByIdScoped("w1", "org1");
        assertNotNull(updated);
        assertEquals(4.5, updated.reputationScore, 0.001);
    }

    @Test
    public void addReputationEvent_and_getAverage() {
        Worker w = new Worker("w1", "u1", "org1", "Frank", "AVAILABLE", 0, 3.0, "zone1");
        repo.insert(w);
        repo.addReputationEvent(new ReputationEvent("e1", "w1", "TASK_COMPLETED", 0.5, "t1", "good"));
        repo.addReputationEvent(new ReputationEvent("e2", "w1", "TASK_COMPLETED", 1.0, "t2", "great"));
        double avg = repo.getAverageReputation("w1");
        assertEquals(0.75, avg, 0.001);
    }

    // -----------------------------------------------------------------------
    // Status filtering
    // -----------------------------------------------------------------------

    @Test
    public void getWorkersByStatus_filtersCorrectly() {
        repo.insert(new Worker("w1", "u1", "org1", "A", "AVAILABLE", 0, 3.0, "z1"));
        repo.insert(new Worker("w2", "u2", "org1", "B", "BUSY", 2, 3.0, "z1"));
        repo.insert(new Worker("w3", "u3", "org1", "C", "AVAILABLE", 0, 3.0, "z1"));
        List<Worker> available = repo.getWorkersByStatus("org1", "AVAILABLE");
        assertEquals(2, available.size());
    }

    @Test
    public void update_changesWorkerFields() {
        Worker w = new Worker("w1", "u1", "org1", "Test", "AVAILABLE", 0, 3.0, "zone1");
        repo.insert(w);
        Worker updated = new Worker("w1", "u1", "org1", "Test", "BUSY", 2, 4.0, "zone2");
        repo.update(updated);
        Worker found = repo.getByIdScoped("w1", "org1");
        assertNotNull(found);
        assertEquals("BUSY", found.status);
        assertEquals(2, found.currentWorkload);
        assertEquals(4.0, found.reputationScore, 0.001);
    }
}
