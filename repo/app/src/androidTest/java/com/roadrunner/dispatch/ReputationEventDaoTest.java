package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.ReputationEventDao;
import com.roadrunner.dispatch.infrastructure.db.entity.ReputationEventEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReputationEventDaoTest {

    private AppDatabase db;
    private ReputationEventDao dao;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.reputationEventDao();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void insert_andGetRecentEvents_returnsEvents() {
        dao.insert(new ReputationEventEntity("e1", "w1", "TASK_COMPLETED", 1.0, "t1", "Good", NOW));
        dao.insert(new ReputationEventEntity("e2", "w1", "RATING_RECEIVED", 0.5, null, "Nice", NOW + 1));

        List<ReputationEventEntity> events = dao.getRecentEvents("w1", 10);
        assertEquals(2, events.size());
    }

    @Test
    public void getAverageScore_computesCorrectly() {
        dao.insert(new ReputationEventEntity("e1", "w1", "TASK_COMPLETED", 4.0, "t1", null, NOW));
        dao.insert(new ReputationEventEntity("e2", "w1", "TASK_COMPLETED", 2.0, "t2", null, NOW));

        double avg = dao.getAverageScore("w1");
        assertEquals(3.0, avg, 0.001);
    }

    @Test
    public void getAverageScore_noEvents_returnsZero() {
        double avg = dao.getAverageScore("nonexistent");
        assertEquals(0.0, avg, 0.001);
    }

    @Test
    public void getRecentEvents_respectsLimit() {
        for (int i = 0; i < 10; i++) {
            dao.insert(new ReputationEventEntity("e" + i, "w1", "TASK_COMPLETED",
                    1.0, null, null, NOW + i));
        }

        List<ReputationEventEntity> limited = dao.getRecentEvents("w1", 5);
        assertEquals(5, limited.size());
    }

    @Test
    public void getRecentEvents_filtersbyWorker() {
        dao.insert(new ReputationEventEntity("e1", "w1", "TASK_COMPLETED", 1.0, null, null, NOW));
        dao.insert(new ReputationEventEntity("e2", "w2", "TASK_COMPLETED", 2.0, null, null, NOW));

        List<ReputationEventEntity> w1Events = dao.getRecentEvents("w1", 10);
        assertEquals(1, w1Events.size());
    }

    @Test
    public void insert_duplicateId_throws() {
        dao.insert(new ReputationEventEntity("e1", "w1", "TASK_COMPLETED", 1.0, null, null, NOW));
        try {
            dao.insert(new ReputationEventEntity("e1", "w1", "TASK_FAILED", -1.0, null, null, NOW));
            fail("Should throw on duplicate insert");
        } catch (Exception e) {
            // Expected — ABORT strategy
        }
    }

    @Test
    public void insert_withNullableFields_succeeds() {
        dao.insert(new ReputationEventEntity("e1", "w1", "PENALTY", -2.0, null, null, NOW));

        List<ReputationEventEntity> events = dao.getRecentEvents("w1", 10);
        assertEquals(1, events.size());
        assertNull(events.get(0).taskId);
        assertNull(events.get(0).notes);
    }
}
