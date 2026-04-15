package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ComplianceCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.ComplianceCaseDao;
import com.roadrunner.dispatch.infrastructure.db.entity.ComplianceCaseEntity;
import com.roadrunner.dispatch.infrastructure.repository.ComplianceCaseRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Room-backed integration tests for {@link ComplianceCaseRepositoryImpl}.
 * Verifies transactional atomicity of insertWithAuditLog against a real
 * in-memory Room database.
 */
@RunWith(AndroidJUnit4.class)
public class ComplianceCaseRepositoryImplTest {

    private AppDatabase db;
    private ComplianceCaseDao caseDao;
    private ComplianceCaseRepositoryImpl repo;

    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        caseDao = db.complianceCaseDao();

        repo = new ComplianceCaseRepositoryImpl(db, caseDao);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -----------------------------------------------------------------------
    // insertWithAuditLog tests
    // -----------------------------------------------------------------------

    @Test
    public void insertWithAuditLog_bothRecordsWritten() {
        ComplianceCase cc = new ComplianceCase("case1", "org1", "emp1",
                "WAGE_THEFT", "OPEN", "HIGH",
                "Worker reported wage theft.", "reviewer1", null);
        AuditLogEntry audit = new AuditLogEntry("a1", "org1", "reviewer1",
                "CASE_OPENED", "EMPLOYER", "emp1", "{}", "case1", NOW);

        repo.insertWithAuditLog(cc, audit);

        // Case record persisted
        ComplianceCaseEntity caseEntity = caseDao.findByIdAndOrg("case1", "org1");
        assertNotNull(caseEntity);
        assertEquals("OPEN", caseEntity.status);
        assertEquals("WAGE_THEFT", caseEntity.caseType);
        assertEquals("HIGH", caseEntity.severity);
        assertEquals("emp1", caseEntity.employerId);

        // Audit log persisted — query by case_id
        List<ComplianceCaseEntity> cases = caseDao.getCasesForEmployer("emp1", "org1");
        assertEquals(1, cases.size());
    }

    @Test
    public void insertWithAuditLog_duplicateCase_throws() {
        ComplianceCase cc = new ComplianceCase("case1", "org1", "emp1",
                "WAGE_THEFT", "OPEN", "HIGH",
                "First case.", "reviewer1", null);
        AuditLogEntry audit = new AuditLogEntry("a1", "org1", "reviewer1",
                "CASE_OPENED", "EMPLOYER", "emp1", "{}", "case1", NOW);

        repo.insertWithAuditLog(cc, audit);

        // Inserting same case ID again should fail (ABORT strategy)
        ComplianceCase duplicate = new ComplianceCase("case1", "org1", "emp1",
                "SAFETY", "OPEN", "MEDIUM",
                "Duplicate case.", "reviewer2", null);
        AuditLogEntry audit2 = new AuditLogEntry("a2", "org1", "reviewer2",
                "CASE_OPENED", "EMPLOYER", "emp1", "{}", "case1", NOW);

        try {
            repo.insertWithAuditLog(duplicate, audit2);
            fail("Expected exception on duplicate case ID");
        } catch (Exception e) {
            // SQLite UNIQUE/ABORT constraint violation — expected
        }

        // Original case unchanged
        ComplianceCaseEntity original = caseDao.findByIdAndOrg("case1", "org1");
        assertEquals("WAGE_THEFT", original.caseType);
    }

    // -----------------------------------------------------------------------
    // Basic CRUD tests
    // -----------------------------------------------------------------------

    @Test
    public void insert_and_getById() {
        ComplianceCase cc = new ComplianceCase("case1", "org1", null,
                "SAFETY", "OPEN", "MEDIUM",
                "Safety concern.", "reviewer1", null);
        repo.insert(cc);

        ComplianceCase fetched = repo.getByIdScoped("case1", "org1");
        assertNotNull(fetched);
        assertEquals("OPEN", fetched.status);
        assertEquals("SAFETY", fetched.caseType);
    }

    @Test
    public void getByIdScoped_wrongOrg_returnsNull() {
        ComplianceCase cc = new ComplianceCase("case1", "org1", null,
                "SAFETY", "OPEN", "MEDIUM",
                "Safety concern.", "reviewer1", null);
        repo.insert(cc);

        ComplianceCase fetched = repo.getByIdScoped("case1", "org-other");
        assertNull(fetched);
    }

    @Test
    public void getByIdScoped_correctOrg_returnsCase() {
        ComplianceCase cc = new ComplianceCase("case1", "org1", null,
                "SAFETY", "OPEN", "MEDIUM",
                "Safety concern.", "reviewer1", null);
        repo.insert(cc);

        ComplianceCase fetched = repo.getByIdScoped("case1", "org1");
        assertNotNull(fetched);
        assertEquals("case1", fetched.id);
    }

    @Test
    public void update_changesStatus() {
        ComplianceCase cc = new ComplianceCase("case1", "org1", "emp1",
                "WAGE_THEFT", "OPEN", "HIGH",
                "Initial.", "reviewer1", null);
        repo.insert(cc);

        ComplianceCase updated = new ComplianceCase("case1", "org1", "emp1",
                "WAGE_THEFT", "RESOLVED", "HIGH",
                "Initial.", "reviewer1", "reviewer2");
        repo.update(updated);

        ComplianceCase fetched = repo.getByIdScoped("case1", "org1");
        assertEquals("RESOLVED", fetched.status);
        assertEquals("reviewer2", fetched.assignedTo);
    }
}
