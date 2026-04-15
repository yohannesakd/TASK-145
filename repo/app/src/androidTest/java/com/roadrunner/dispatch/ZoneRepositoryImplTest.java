package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.ZoneDao;
import com.roadrunner.dispatch.infrastructure.repository.ZoneRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ZoneRepositoryImplTest {

    private AppDatabase db;
    private ZoneDao zoneDao;
    private ZoneRepositoryImpl repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        zoneDao = db.zoneDao();
        repo = new ZoneRepositoryImpl(zoneDao);
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
        Zone z = new Zone("z1", "org1", "Zone A", 5, "High priority zone");
        repo.insert(z);
        Zone found = repo.getByIdScoped("z1", "org1");
        assertNotNull(found);
        assertEquals("Zone A", found.name);
        assertEquals(5, found.score);
        assertEquals("High priority zone", found.description);
    }

    @Test
    public void getByIdScoped_wrongOrg_returnsNull() {
        repo.insert(new Zone("z1", "org1", "Zone A", 3, "desc"));
        assertNull(repo.getByIdScoped("z1", "org2"));
    }

    @Test
    public void getByIdScoped_correctOrg_returnsZone() {
        repo.insert(new Zone("z1", "org1", "Zone A", 3, "desc"));
        Zone found = repo.getByIdScoped("z1", "org1");
        assertNotNull(found);
        assertEquals("z1", found.id);
    }

    // -----------------------------------------------------------------------
    // List
    // -----------------------------------------------------------------------

    @Test
    public void getZones_filtersOrg() {
        repo.insert(new Zone("z1", "org1", "Zone A", 1, null));
        repo.insert(new Zone("z2", "org1", "Zone B", 2, null));
        repo.insert(new Zone("z3", "org2", "Zone C", 3, null));

        List<Zone> zones = repo.getZones("org1");
        assertEquals(2, zones.size());
    }

    // -----------------------------------------------------------------------
    // Update
    // -----------------------------------------------------------------------

    @Test
    public void update_changesFields() {
        repo.insert(new Zone("z1", "org1", "Zone A", 1, "old"));
        repo.update(new Zone("z1", "org1", "Zone A Prime", 5, "updated"));

        Zone found = repo.getByIdScoped("z1", "org1");
        assertNotNull(found);
        assertEquals("Zone A Prime", found.name);
        assertEquals(5, found.score);
        assertEquals("updated", found.description);
    }

    @Test
    public void insert_nullDescription() {
        repo.insert(new Zone("z1", "org1", "Zone X", 3, null));
        Zone found = repo.getByIdScoped("z1", "org1");
        assertNotNull(found);
        assertNull(found.description);
    }
}
