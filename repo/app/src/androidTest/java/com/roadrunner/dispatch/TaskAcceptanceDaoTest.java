package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.TaskAcceptanceDao;
import com.roadrunner.dispatch.infrastructure.db.entity.TaskAcceptanceEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.TaskEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TaskAcceptanceDaoTest {

    private AppDatabase db;
    private TaskAcceptanceDao dao;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.taskAcceptanceDao();
        // Seed a task to satisfy FK constraint
        db.taskDao().insert(new TaskEntity("t1", "org1", "Task", "Desc", "OPEN",
                "GRAB_ORDER", 5, "zone1", NOW, NOW + 3600000, null, "disp1", NOW, NOW));
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void insert_andGetAcceptancesForTask_returnsList() {
        dao.insert(new TaskAcceptanceEntity("a1", "t1", "w1", NOW, "ACCEPTED"));

        List<TaskAcceptanceEntity> acceptances = dao.getAcceptancesForTask("t1");
        assertEquals(1, acceptances.size());
        assertEquals("w1", acceptances.get(0).acceptedBy);
    }

    @Test
    public void findByTaskAndWorker_returnsMatch() {
        dao.insert(new TaskAcceptanceEntity("a1", "t1", "w1", NOW, "ACCEPTED"));

        TaskAcceptanceEntity found = dao.findByTaskAndWorker("t1", "w1");
        assertNotNull(found);
        assertEquals("ACCEPTED", found.status);
    }

    @Test
    public void findByTaskAndWorker_noMatch_returnsNull() {
        assertNull(dao.findByTaskAndWorker("t1", "nonexistent"));
    }

    @Test
    public void multipleAcceptances_forSameTask() {
        dao.insert(new TaskAcceptanceEntity("a1", "t1", "w1", NOW, "ACCEPTED"));
        dao.insert(new TaskAcceptanceEntity("a2", "t1", "w2", NOW, "REJECTED"));

        List<TaskAcceptanceEntity> acceptances = dao.getAcceptancesForTask("t1");
        assertEquals(2, acceptances.size());
    }

    @Test
    public void insert_duplicateId_throws() {
        dao.insert(new TaskAcceptanceEntity("a1", "t1", "w1", NOW, "ACCEPTED"));
        try {
            dao.insert(new TaskAcceptanceEntity("a1", "t1", "w2", NOW, "ACCEPTED"));
            fail("Should throw on duplicate insert");
        } catch (Exception e) {
            // Expected — ABORT strategy
        }
    }

    @Test
    public void getAcceptancesForTask_emptyForUnknownTask() {
        List<TaskAcceptanceEntity> acceptances = dao.getAcceptancesForTask("unknown");
        assertTrue(acceptances.isEmpty());
    }
}
