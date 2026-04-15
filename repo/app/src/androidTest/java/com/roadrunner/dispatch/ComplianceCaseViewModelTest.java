package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.ComplianceCase;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.usecase.EnforceViolationUseCase;
import com.roadrunner.dispatch.core.domain.usecase.OpenCaseUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.entity.EmployerEntity;
import com.roadrunner.dispatch.infrastructure.repository.AuditLogRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ComplianceCaseRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.EmployerRepositoryImpl;
import com.roadrunner.dispatch.presentation.compliance.cases.ComplianceCaseViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented tests for {@link ComplianceCaseViewModel} wired to real Room DB.
 */
@RunWith(AndroidJUnit4.class)
public class ComplianceCaseViewModelTest {

    private AppDatabase db;
    private ComplianceCaseViewModel viewModel;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        ComplianceCaseRepositoryImpl caseRepo =
                new ComplianceCaseRepositoryImpl(db, db.complianceCaseDao());
        AuditLogRepositoryImpl auditRepo = new AuditLogRepositoryImpl(db.auditLogDao());
        EmployerRepositoryImpl employerRepo = new EmployerRepositoryImpl(db, db.employerDao());

        OpenCaseUseCase openCaseUseCase = new OpenCaseUseCase(caseRepo, auditRepo, employerRepo);
        EnforceViolationUseCase enforceUseCase = new EnforceViolationUseCase(employerRepo, auditRepo);

        viewModel = new ComplianceCaseViewModel(caseRepo, auditRepo, openCaseUseCase, enforceUseCase);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private void seedEmployer() {
        db.employerDao().insert(new EmployerEntity(
                "emp1", "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62701",
                "VERIFIED", 0, 0L, false, NOW, NOW));
    }

    @Test
    public void openCase_validInput_postsOpenedCase() throws InterruptedException {
        seedEmployer();
        CountDownLatch latch = new CountDownLatch(1);
        final ComplianceCase[] observed = {null};

        viewModel.getOpenedCase().observeForever(cc -> {
            if (cc != null) {
                observed[0] = cc;
                latch.countDown();
            }
        });

        viewModel.openCase("org1", "emp1", "WAGE_THEFT", "HIGH",
                "Suspected violation", "reviewer1", "COMPLIANCE_REVIEWER");

        assertTrue("Case should open within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertEquals("WAGE_THEFT", observed[0].caseType);
        assertEquals("HIGH", observed[0].severity);
    }

    @Test
    public void openCase_wrongRole_postsError() throws InterruptedException {
        seedEmployer();
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        viewModel.openCase("org1", "emp1", "SAFETY", "MEDIUM",
                "Concern", "admin1", "ADMIN");

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void enforceViolation_warning_postsEnforcementResult() throws InterruptedException {
        seedEmployer();
        CountDownLatch latch = new CountDownLatch(1);
        final Employer[] observed = {null};

        viewModel.getEnforcementResult().observeForever(emp -> {
            if (emp != null) {
                observed[0] = emp;
                latch.countDown();
            }
        });

        viewModel.enforceViolation("emp1", "WARNING", "reviewer1", null,
                "org1", false, "COMPLIANCE_REVIEWER");

        assertTrue("Enforcement should complete within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertEquals(1, observed[0].warningCount);
    }

    @Test
    public void enforceViolation_wrongRole_postsError() throws InterruptedException {
        seedEmployer();
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        viewModel.enforceViolation("emp1", "WARNING", "admin1", null,
                "org1", false, "ADMIN");

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void getCases_returnsLiveData() {
        assertNotNull(viewModel.getCases("org1"));
    }

    @Test
    public void getCasesByStatus_returnsLiveData() {
        assertNotNull(viewModel.getCasesByStatus("org1", "OPEN"));
    }

    @Test
    public void getAuditLogsForCase_returnsLiveData() {
        assertNotNull(viewModel.getAuditLogsForCase("case1", "org1"));
    }

    @Test
    public void getAllAuditLogs_returnsLiveData() {
        assertNotNull(viewModel.getAllAuditLogs("org1"));
    }
}
