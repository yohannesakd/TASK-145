package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.ComplianceCase;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.model.Report;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.usecase.EnforceViolationUseCase;
import com.roadrunner.dispatch.core.domain.usecase.FileReportUseCase;
import com.roadrunner.dispatch.core.domain.usecase.OpenCaseUseCase;
import com.roadrunner.dispatch.core.domain.usecase.VerifyEmployerUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.entity.EmployerEntity;
import com.roadrunner.dispatch.infrastructure.repository.AuditLogRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ComplianceCaseRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.EmployerRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.OrderRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ReportRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.TaskRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * End-to-end instrumented test for the compliance workflow:
 * Employer verification → Case creation → Report filing → Enforcement actions.
 */
@RunWith(AndroidJUnit4.class)
public class ComplianceFlowIntegrationTest {

    private AppDatabase db;
    private EmployerRepositoryImpl employerRepo;
    private VerifyEmployerUseCase verifyEmployerUseCase;
    private OpenCaseUseCase openCaseUseCase;
    private FileReportUseCase fileReportUseCase;
    private EnforceViolationUseCase enforceViolationUseCase;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        employerRepo = new EmployerRepositoryImpl(db, db.employerDao());
        ComplianceCaseRepositoryImpl caseRepo =
                new ComplianceCaseRepositoryImpl(db, db.complianceCaseDao());
        AuditLogRepositoryImpl auditRepo = new AuditLogRepositoryImpl(db.auditLogDao());
        ReportRepositoryImpl reportRepo = new ReportRepositoryImpl(db.reportDao());
        OrderRepositoryImpl orderRepo = new OrderRepositoryImpl(db, db.orderDao(),
                db.orderItemDao(), db.orderDiscountDao(), db.discountRuleDao(),
                db.shippingTemplateDao(), db.auditLogDao());
        TaskRepositoryImpl taskRepo = new TaskRepositoryImpl(db, db.taskDao(), db.taskAcceptanceDao());

        verifyEmployerUseCase = new VerifyEmployerUseCase(employerRepo);
        openCaseUseCase = new OpenCaseUseCase(caseRepo, auditRepo, employerRepo);
        fileReportUseCase = new FileReportUseCase(reportRepo, employerRepo, orderRepo, taskRepo);
        enforceViolationUseCase = new EnforceViolationUseCase(employerRepo, auditRepo);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private void seedVerifiedEmployer() {
        db.employerDao().insert(new EmployerEntity(
                "emp1", "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62701",
                "VERIFIED", 0, 0L, false, NOW, NOW));
    }

    private Employer makeEmployer(String legalName, String ein, String address,
                                   String city, String state, String zip) {
        return new Employer(UUID.randomUUID().toString(), "org1", legalName, ein,
                address, city, state, zip, "PENDING", 0, 0L, false);
    }

    // -----------------------------------------------------------------------
    // Employer verification
    // -----------------------------------------------------------------------

    @Test
    public void verifyEmployer_validData_succeeds() {
        Employer emp = makeEmployer("Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62701");
        Result<Employer> result = verifyEmployerUseCase.execute(emp, "COMPLIANCE_REVIEWER");
        assertTrue("Employer verification should succeed: " + result.getFirstError(),
                result.isSuccess());
        Employer verified = result.getData();
        assertNotNull(verified);
        assertEquals("Acme Corp", verified.legalName);
        assertEquals("12-3456789", verified.ein);
    }

    @Test
    public void verifyEmployer_invalidEin_fails() {
        Employer emp = makeEmployer("Bad Corp", "INVALID",
                "123 Main St", "City", "IL", "60601");
        Result<Employer> result = verifyEmployerUseCase.execute(emp, "COMPLIANCE_REVIEWER");
        assertFalse("Invalid EIN should fail", result.isSuccess());
    }

    @Test
    public void verifyEmployer_wrongRole_rejected() {
        Employer emp = makeEmployer("Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62701");
        Result<Employer> result = verifyEmployerUseCase.execute(emp, "WORKER");
        assertFalse("Worker should not verify employers", result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Case creation
    // -----------------------------------------------------------------------

    @Test
    public void openCase_forVerifiedEmployer_succeeds() {
        seedVerifiedEmployer();

        Result<ComplianceCase> result = openCaseUseCase.execute(
                "org1", "emp1", "WAGE_THEFT", "HIGH",
                "Suspected wage theft", "reviewer1", "COMPLIANCE_REVIEWER");
        assertTrue("Case creation should succeed: " + result.getFirstError(),
                result.isSuccess());
        ComplianceCase cc = result.getData();
        assertNotNull(cc);
        assertEquals("WAGE_THEFT", cc.caseType);
        assertEquals("HIGH", cc.severity);
        assertEquals("emp1", cc.employerId);
    }

    @Test
    public void openCase_nonComplianceRole_rejected() {
        seedVerifiedEmployer();

        Result<ComplianceCase> result = openCaseUseCase.execute(
                "org1", "emp1", "SAFETY", "MEDIUM",
                "Safety concern", "admin1", "ADMIN");
        assertFalse("Admin should not open cases", result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Report filing
    // -----------------------------------------------------------------------

    @Test
    public void fileReport_forEmployer_succeeds() {
        seedVerifiedEmployer();

        Result<Report> result = fileReportUseCase.execute(
                "org1", "reviewer1", "EMPLOYER", "emp1",
                "Documented wage theft evidence",
                "file://evidence.jpg", "sha256abc",
                "COMPLIANCE_REVIEWER", null);
        assertTrue("Report filing should succeed: " + result.getFirstError(),
                result.isSuccess());
        Report report = result.getData();
        assertNotNull(report);
        assertEquals("EMPLOYER", report.targetType);
        assertEquals("emp1", report.targetId);
        assertEquals("FILED", report.status);
    }

    @Test
    public void fileReport_workerCanFile() {
        seedVerifiedEmployer();

        Result<Report> result = fileReportUseCase.execute(
                "org1", "worker1", "EMPLOYER", "emp1",
                "I witnessed this", null, null, "WORKER", null);
        assertTrue("Worker should be able to file reports: " + result.getFirstError(),
                result.isSuccess());
    }

    @Test
    public void fileReport_dispatcherRole_rejected() {
        seedVerifiedEmployer();

        Result<Report> result = fileReportUseCase.execute(
                "org1", "disp1", "EMPLOYER", "emp1",
                "Report attempt", null, null, "DISPATCHER", null);
        assertFalse("Dispatcher should not file reports", result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Enforcement actions
    // -----------------------------------------------------------------------

    @Test
    public void enforceViolation_warning_updatesEmployer() {
        seedVerifiedEmployer();

        Result<?> result = enforceViolationUseCase.execute(
                "emp1", "WARNING", "reviewer1", null, "org1", false,
                "COMPLIANCE_REVIEWER");
        assertTrue("Warning should succeed: " + result.getFirstError(),
                result.isSuccess());

        Employer emp = employerRepo.getByIdScoped("emp1", "org1");
        assertNotNull(emp);
        assertEquals(1, emp.warningCount);
    }

    @Test
    public void enforceViolation_twoWarningsThenSuspension() {
        seedVerifiedEmployer();

        // Warning 1
        enforceViolationUseCase.execute(
                "emp1", "WARNING", "r1", null, "org1", false,
                "COMPLIANCE_REVIEWER");
        // Warning 2
        enforceViolationUseCase.execute(
                "emp1", "WARNING", "r1", null, "org1", false,
                "COMPLIANCE_REVIEWER");

        Employer afterWarnings = employerRepo.getByIdScoped("emp1", "org1");
        assertEquals(2, afterWarnings.warningCount);

        // Now suspend
        Result<?> suspendResult = enforceViolationUseCase.execute(
                "emp1", "SUSPEND_7", "r1", null, "org1", false,
                "COMPLIANCE_REVIEWER");
        assertTrue("Suspension should succeed after 2 warnings: " + suspendResult.getFirstError(),
                suspendResult.isSuccess());

        Employer suspended = employerRepo.getByIdScoped("emp1", "org1");
        assertEquals("SUSPENDED", suspended.status);
        assertTrue(suspended.suspendedUntil > NOW);
    }

    @Test
    public void enforceViolation_nonComplianceRole_rejected() {
        seedVerifiedEmployer();

        Result<?> result = enforceViolationUseCase.execute(
                "emp1", "WARNING", "admin1", null, "org1", false, "ADMIN");
        assertFalse("Admin should not enforce violations", result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Full compliance flow
    // -----------------------------------------------------------------------

    @Test
    public void fullFlow_verifyEmployerOpenCaseFileReportEnforce() {
        // Step 1: Verify employer
        Employer emp = makeEmployer("Test Corp", "98-7654321",
                "456 Oak Ave", "Chicago", "IL", "60601");
        Result<Employer> verifyResult = verifyEmployerUseCase.execute(
                emp, "COMPLIANCE_REVIEWER");
        assertTrue(verifyResult.isSuccess());
        String empId = verifyResult.getData().id;

        // Step 2: Open case
        Result<ComplianceCase> caseResult = openCaseUseCase.execute(
                "org1", empId, "SAFETY", "HIGH",
                "Workplace safety violation", "reviewer1", "COMPLIANCE_REVIEWER");
        assertTrue(caseResult.isSuccess());
        String caseId = caseResult.getData().id;

        // Step 3: File report linked to case
        Result<Report> reportResult = fileReportUseCase.execute(
                "org1", "reviewer1", "EMPLOYER", empId,
                "Detailed safety findings", "file://photo.jpg",
                "sha256xyz", "COMPLIANCE_REVIEWER", caseId);
        assertTrue(reportResult.isSuccess());
        assertEquals(caseId, reportResult.getData().caseId);

        // Step 4: Enforce warning
        Result<?> warnResult = enforceViolationUseCase.execute(
                empId, "WARNING", "reviewer1", caseId, "org1", false,
                "COMPLIANCE_REVIEWER");
        assertTrue(warnResult.isSuccess());

        // Verify employer state
        Employer employer = employerRepo.getByIdScoped(empId, "org1");
        assertEquals(1, employer.warningCount);
    }
}
