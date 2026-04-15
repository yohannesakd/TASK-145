package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.Report;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.ReportDao;
import com.roadrunner.dispatch.infrastructure.db.entity.ReportEntity;
import com.roadrunner.dispatch.infrastructure.repository.ReportRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReportRepositoryImplTest {

    private AppDatabase db;
    private ReportDao reportDao;
    private ReportRepositoryImpl repo;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        reportDao = db.reportDao();
        repo = new ReportRepositoryImpl(reportDao);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -----------------------------------------------------------------------
    // Insert and query
    // -----------------------------------------------------------------------

    @Test
    public void fileReport_and_getByIdScoped() {
        Report r = new Report("r1", "org1", "case1", "user1", "EMPLOYER",
                "emp1", "Wage theft observed", "file://evidence.jpg",
                "abc123hash", "FILED");
        repo.fileReport(r);
        Report found = repo.getByIdScoped("r1", "org1");
        assertNotNull(found);
        assertEquals("EMPLOYER", found.targetType);
        assertEquals("emp1", found.targetId);
        assertEquals("Wage theft observed", found.description);
        assertEquals("abc123hash", found.evidenceHash);
        assertEquals("FILED", found.status);
    }

    @Test
    public void getByIdScoped_wrongOrg_returnsNull() {
        Report r = new Report("r1", "org1", null, "user1", "TASK",
                "t1", "Safety issue", null, null, "FILED");
        repo.fileReport(r);
        assertNull(repo.getByIdScoped("r1", "org2"));
    }

    @Test
    public void getByIdScoped_correctOrg_returnsReport() {
        Report r = new Report("r1", "org1", null, "user1", "WORKER",
                "w1", "Misconduct", null, null, "FILED");
        repo.fileReport(r);
        Report found = repo.getByIdScoped("r1", "org1");
        assertNotNull(found);
        assertEquals("r1", found.id);
    }

    // -----------------------------------------------------------------------
    // Update
    // -----------------------------------------------------------------------

    @Test
    public void update_changesStatus() {
        Report r = new Report("r1", "org1", "case1", "user1", "EMPLOYER",
                "emp1", "Issue", null, null, "FILED");
        repo.fileReport(r);
        Report updated = new Report("r1", "org1", "case1", "user1", "EMPLOYER",
                "emp1", "Issue", null, null, "REVIEWED");
        repo.update(updated);
        Report found = repo.getByIdScoped("r1", "org1");
        assertNotNull(found);
        assertEquals("REVIEWED", found.status);
    }

    @Test
    public void fileReport_withNullOptionalFields() {
        Report r = new Report("r1", "org1", null, "user1", "ORDER",
                "o1", "Suspicious order", null, null, "FILED");
        repo.fileReport(r);
        Report found = repo.getByIdScoped("r1", "org1");
        assertNotNull(found);
        assertNull(found.caseId);
        assertNull(found.evidenceUri);
        assertNull(found.evidenceHash);
    }

    @Test
    public void fileReport_withEvidence_preservesHashAndUri() {
        Report r = new Report("r1", "org1", "case1", "user1", "TASK",
                "t1", "Evidence report", "file://doc.pdf",
                "sha256abcdef", "FILED");
        repo.fileReport(r);
        Report found = repo.getByIdScoped("r1", "org1");
        assertNotNull(found);
        assertEquals("file://doc.pdf", found.evidenceUri);
        assertEquals("sha256abcdef", found.evidenceHash);
    }
}
