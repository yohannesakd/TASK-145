package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.Report;
import com.roadrunner.dispatch.core.domain.usecase.FileReportUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.entity.EmployerEntity;
import com.roadrunner.dispatch.infrastructure.repository.EmployerRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.OrderRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ReportRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.TaskRepositoryImpl;
import com.roadrunner.dispatch.presentation.compliance.reports.ReportViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented integration tests for {@link ReportViewModel} wired to real Room DB.
 * Exercises report filing, error handling, and LiveData accessors.
 */
@RunWith(AndroidJUnit4.class)
public class ReportViewModelTest {

    private AppDatabase db;
    private ReportViewModel viewModel;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        ReportRepositoryImpl reportRepo = new ReportRepositoryImpl(db.reportDao());
        EmployerRepositoryImpl employerRepo = new EmployerRepositoryImpl(db, db.employerDao());
        OrderRepositoryImpl orderRepo = new OrderRepositoryImpl(db, db.orderDao(),
                db.orderItemDao(), db.orderDiscountDao(), db.discountRuleDao(),
                db.shippingTemplateDao(), db.auditLogDao());
        TaskRepositoryImpl taskRepo = new TaskRepositoryImpl(db, db.taskDao(), db.taskAcceptanceDao());

        FileReportUseCase fileReportUseCase = new FileReportUseCase(
                reportRepo, employerRepo, orderRepo, taskRepo);

        viewModel = new ReportViewModel(reportRepo, fileReportUseCase);
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

    // -----------------------------------------------------------------------
    // Report filing
    // -----------------------------------------------------------------------

    @Test
    public void fileReport_validInput_postsFiledReport() throws InterruptedException {
        seedEmployer();
        CountDownLatch latch = new CountDownLatch(1);
        final Report[] observed = {null};

        viewModel.getFiledReport().observeForever(report -> {
            if (report != null) {
                observed[0] = report;
                latch.countDown();
            }
        });

        viewModel.fileReport("org1", "reviewer1", "EMPLOYER", "emp1",
                "Documented violation", "file://evidence.jpg", "sha256hash",
                "COMPLIANCE_REVIEWER");

        assertTrue("Report should be filed within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertEquals("EMPLOYER", observed[0].targetType);
        assertEquals("emp1", observed[0].targetId);
        assertEquals("FILED", observed[0].status);
    }

    @Test
    public void fileReport_withCaseId_linksToCaseCorrectly() throws InterruptedException {
        seedEmployer();
        CountDownLatch latch = new CountDownLatch(1);
        final Report[] observed = {null};

        viewModel.getFiledReport().observeForever(report -> {
            if (report != null) {
                observed[0] = report;
                latch.countDown();
            }
        });

        viewModel.fileReport("org1", "reviewer1", "EMPLOYER", "emp1",
                "Linked report", null, null, "COMPLIANCE_REVIEWER", "case123");

        assertTrue("Report should be filed within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertEquals("case123", observed[0].caseId);
    }

    @Test
    public void fileReport_unauthorizedRole_postsError() throws InterruptedException {
        seedEmployer();
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        viewModel.fileReport("org1", "disp1", "EMPLOYER", "emp1",
                "Unauthorized attempt", null, null, "DISPATCHER");

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void fileReport_workerRole_succeeds() throws InterruptedException {
        seedEmployer();
        CountDownLatch latch = new CountDownLatch(1);
        final Report[] observed = {null};

        viewModel.getFiledReport().observeForever(report -> {
            if (report != null) {
                observed[0] = report;
                latch.countDown();
            }
        });

        viewModel.fileReport("org1", "worker1", "EMPLOYER", "emp1",
                "Worker report", null, null, "WORKER");

        assertTrue("Report should be filed within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
    }

    // -----------------------------------------------------------------------
    // LiveData accessors
    // -----------------------------------------------------------------------

    @Test
    public void getReports_returnsLiveData() {
        assertNotNull(viewModel.getReports("org1"));
    }

    @Test
    public void getReportsForCase_returnsLiveData() {
        assertNotNull(viewModel.getReportsForCase("case1", "org1"));
    }
}
