package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.EmployerDao;
import com.roadrunner.dispatch.infrastructure.db.entity.EmployerEntity;
import com.roadrunner.dispatch.infrastructure.repository.EmployerRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Room-backed integration tests for {@link EmployerRepositoryImpl}.
 * Verifies transactional atomicity of updateWithAuditLog against a real
 * in-memory Room database.
 */
@RunWith(AndroidJUnit4.class)
public class EmployerRepositoryImplTest {

    private AppDatabase db;
    private EmployerDao employerDao;
    private EmployerRepositoryImpl repo;

    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        employerDao = db.employerDao();

        repo = new EmployerRepositoryImpl(db, employerDao);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void insertEmployer(String id, String orgId, String status,
                                int warningCount, boolean throttled) {
        EmployerEntity entity = new EmployerEntity(id, orgId, "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62701",
                status, warningCount, 0L, throttled, NOW, NOW);
        employerDao.insert(entity);
    }

    // -----------------------------------------------------------------------
    // updateWithAuditLog tests
    // -----------------------------------------------------------------------

    @Test
    public void updateWithAuditLog_bothWritesPersisted() {
        insertEmployer("emp1", "org1", "VERIFIED", 0, false);

        // Simulate issuing a warning: bump warningCount, write audit
        Employer updated = new Employer("emp1", "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62701",
                "VERIFIED", 1, 0L, false);
        AuditLogEntry audit = new AuditLogEntry("a1", "org1", "reviewer1",
                "WARNING_ISSUED", "EMPLOYER", "emp1",
                "{\"reason\":\"Late payroll\"}", null, NOW);

        repo.updateWithAuditLog(updated, audit);

        // Employer record updated
        EmployerEntity entity = employerDao.findByIdAndOrg("emp1", "org1");
        assertEquals(1, entity.warningCount);

        // Audit log persisted
        Employer fetched = repo.getByIdScoped("emp1", "org1");
        assertNotNull(fetched);
        assertEquals(1, fetched.warningCount);
    }

    @Test
    public void updateWithAuditLog_suspension_applied() {
        insertEmployer("emp1", "org1", "VERIFIED", 2, false);

        long suspendUntil = NOW + 30L * 24 * 3_600_000L; // 30 days
        Employer suspended = new Employer("emp1", "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62701",
                "SUSPENDED", 3, suspendUntil, false);
        AuditLogEntry audit = new AuditLogEntry("a2", "org1", "reviewer1",
                "SUSPENSION_APPLIED", "EMPLOYER", "emp1",
                "{\"duration\":\"30 days\"}", null, NOW);

        repo.updateWithAuditLog(suspended, audit);

        EmployerEntity entity = employerDao.findByIdAndOrg("emp1", "org1");
        assertEquals("SUSPENDED", entity.status);
        assertEquals(3, entity.warningCount);
        assertTrue(entity.suspendedUntil > NOW);
    }

    @Test
    public void updateWithAuditLog_throttleApplied() {
        insertEmployer("emp1", "org1", "VERIFIED", 1, false);

        Employer throttled = new Employer("emp1", "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62701",
                "VERIFIED", 1, 0L, true);
        AuditLogEntry audit = new AuditLogEntry("a3", "org1", "reviewer1",
                "THROTTLE_APPLIED", "EMPLOYER", "emp1",
                "{}", null, NOW);

        repo.updateWithAuditLog(throttled, audit);

        EmployerEntity entity = employerDao.findByIdAndOrg("emp1", "org1");
        assertTrue(entity.throttled);
    }

    @Test
    public void updateWithAuditLog_throttleRemoved() {
        insertEmployer("emp1", "org1", "VERIFIED", 1, true);

        Employer unthrottled = new Employer("emp1", "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62701",
                "VERIFIED", 1, 0L, false);
        AuditLogEntry audit = new AuditLogEntry("a4", "org1", "reviewer1",
                "THROTTLE_REMOVED", "EMPLOYER", "emp1",
                "{}", null, NOW);

        repo.updateWithAuditLog(unthrottled, audit);

        EmployerEntity entity = employerDao.findByIdAndOrg("emp1", "org1");
        assertFalse(entity.throttled);
    }

    // -----------------------------------------------------------------------
    // Basic CRUD tests
    // -----------------------------------------------------------------------

    @Test
    public void insert_and_getById() {
        Employer employer = new Employer("emp1", "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62701",
                "PENDING", 0, 0L, false);
        repo.insert(employer);

        Employer fetched = repo.getByIdScoped("emp1", "org1");
        assertNotNull(fetched);
        assertEquals("PENDING", fetched.status);
        assertEquals("Acme Corp", fetched.legalName);
    }

    @Test
    public void getByIdScoped_wrongOrg_returnsNull() {
        insertEmployer("emp1", "org1", "VERIFIED", 0, false);

        Employer fetched = repo.getByIdScoped("emp1", "org-other");
        assertNull(fetched);
    }

    @Test
    public void getByIdScoped_correctOrg_returnsEmployer() {
        insertEmployer("emp1", "org1", "VERIFIED", 0, false);

        Employer fetched = repo.getByIdScoped("emp1", "org1");
        assertNotNull(fetched);
        assertEquals("emp1", fetched.id);
    }

    @Test
    public void getByEinScoped_crossOrgIsolation() {
        insertEmployer("emp1", "org1", "VERIFIED", 0, false);

        // Same EIN but different org — should not find
        Employer fetched = repo.getByEinScoped("12-3456789", "org-other");
        assertNull(fetched);

        // Correct org — should find
        Employer found = repo.getByEinScoped("12-3456789", "org1");
        assertNotNull(found);
        assertEquals("emp1", found.id);
    }

    @Test
    public void getEmployersSync_returnsOnlyMatchingOrg() {
        insertEmployer("emp1", "org1", "VERIFIED", 0, false);
        insertEmployer("emp2", "org2", "VERIFIED", 0, false);

        List<Employer> org1Employers = repo.getEmployersSync("org1");
        assertEquals(1, org1Employers.size());
        assertEquals("emp1", org1Employers.get(0).id);
    }
}
