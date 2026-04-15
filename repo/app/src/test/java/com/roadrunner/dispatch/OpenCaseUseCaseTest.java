package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ComplianceCase;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.AuditLogRepository;
import com.roadrunner.dispatch.core.domain.repository.ComplianceCaseRepository;
import com.roadrunner.dispatch.core.domain.repository.EmployerRepository;
import com.roadrunner.dispatch.core.domain.usecase.OpenCaseUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class OpenCaseUseCaseTest {

    private StubComplianceCaseRepository caseRepo;
    private StubAuditLogRepository auditRepo;
    private StubEmployerRepository employerRepo;
    private OpenCaseUseCase useCase;

    @Before
    public void setUp() {
        caseRepo     = new StubComplianceCaseRepository();
        auditRepo    = new StubAuditLogRepository();
        employerRepo = new StubEmployerRepository();
        caseRepo.auditRepoRef = auditRepo;
        // Pre-populate a known employer for existing tests
        employerRepo.knownId = "emp-1";
        employerRepo.knownOrgId = "org1";
        useCase = new OpenCaseUseCase(caseRepo, auditRepo, employerRepo);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void validCase_success_statusOpen() {
        Result<ComplianceCase> result = useCase.execute(
                "org1", "emp-1", "WAGE_THEFT", "HIGH",
                "Worker reported wage theft.", "reviewer1", "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
        assertEquals("OPEN", result.getData().status);
    }

    @Test
    public void missingDescription_failure() {
        Result<ComplianceCase> result = useCase.execute(
                "org1", "emp-1", "WAGE_THEFT", "HIGH",
                "   " /* blank description */, "reviewer1", "COMPLIANCE_REVIEWER");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("description"));
    }

    @Test
    public void auditLogEntryCreated_withCaseOpenedAction() {
        useCase.execute("org1", "emp-1", "WAGE_THEFT", "HIGH",
                "Worker reported wage theft.", "reviewer1", "COMPLIANCE_REVIEWER");

        assertFalse(auditRepo.logs.isEmpty());
        AuditLogEntry entry = auditRepo.logs.get(0);
        assertEquals("CASE_OPENED", entry.action);
        assertEquals("EMPLOYER", entry.targetType);
        assertEquals("emp-1", entry.targetId);
    }

    @Test
    public void caseIdGenerated_nonNull() {
        employerRepo.knownId = "emp-2";
        employerRepo.knownOrgId = "org1";
        Result<ComplianceCase> result = useCase.execute(
                "org1", "emp-2", "SAFETY", "MEDIUM",
                "Safety inspection failure documented.", "admin1", "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
        assertNotNull(result.getData().id);
        assertFalse(result.getData().id.isEmpty());
    }

    @Test
    public void workerRole_rejected() {
        Result<ComplianceCase> result = useCase.execute(
                "org1", "emp-1", "WAGE_THEFT", "HIGH",
                "Worker reported wage theft.", "user1", "WORKER");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("unauthorized") ||
                   result.getFirstError().toLowerCase().contains("compliance"));
    }

    @Test
    public void dispatcherRole_rejected() {
        Result<ComplianceCase> result = useCase.execute(
                "org1", "emp-1", "WAGE_THEFT", "HIGH",
                "Worker reported wage theft.", "user2", "DISPATCHER");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("unauthorized") ||
                   result.getFirstError().toLowerCase().contains("compliance"));
    }

    @Test
    public void adminRole_rejected() {
        Result<ComplianceCase> result = useCase.execute("org1", null, "INVESTIGATION", "HIGH",
                "Test case", "admin-user", "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("unauthorized") ||
                   result.getFirstError().toLowerCase().contains("compliance") ||
                   result.getFirstError().toLowerCase().contains("role"));
    }

    // -----------------------------------------------------------------------
    // Employer validation tests
    // -----------------------------------------------------------------------

    @Test
    public void unknownEmployer_failure() {
        Result<ComplianceCase> result = useCase.execute(
                "org1", "emp-unknown", "WAGE_THEFT", "HIGH",
                "Worker reported wage theft.", "reviewer1", "COMPLIANCE_REVIEWER");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("employer"));
    }

    @Test
    public void nullEmployerId_skipsEmployerCheck() {
        Result<ComplianceCase> result = useCase.execute(
                "org1", null, "WAGE_THEFT", "HIGH",
                "General complaint.", "reviewer1", "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
    }

    @Test
    public void emptyEmployerId_skipsEmployerCheck() {
        Result<ComplianceCase> result = useCase.execute(
                "org1", "  ", "WAGE_THEFT", "HIGH",
                "General complaint.", "reviewer1", "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
    }

    @Test
    public void validEmployer_caseOpened() {
        employerRepo.knownId = "emp-valid";
        employerRepo.knownOrgId = "org1";
        Result<ComplianceCase> result = useCase.execute(
                "org1", "emp-valid", "SAFETY", "MEDIUM",
                "Safety concern filed.", "reviewer1", "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
        assertEquals("OPEN", result.getData().status);
        assertEquals("emp-valid", result.getData().employerId);
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubEmployerRepository implements EmployerRepository {
        String knownId;
        String knownOrgId;

        @Override
        public Employer getByIdScoped(String id, String orgId) {
            if (id != null && id.equals(knownId) && orgId != null && orgId.equals(knownOrgId)) {
                return new Employer(id, orgId, "Test Employer", "12-3456789",
                        "123 Main St", "Springfield", "IL", "62701",
                        "ACTIVE", 0, 0L, false);
            }
            return null;
        }

        @Override public Employer getByEinScoped(String ein, String orgId) { return null; }
        @Override public LiveData<List<Employer>> getEmployers(String orgId) { return null; }
        @Override public List<Employer> getEmployersSync(String orgId) { return null; }
        @Override public LiveData<List<Employer>> getEmployersByStatus(String orgId, String status) { return null; }
        @Override public LiveData<List<Employer>> getEmployersFiltered(String orgId, boolean includeThrottled) { return null; }
        @Override public void insert(Employer employer) {}
        @Override public void update(Employer employer) {}
        @Override public void updateWithAuditLog(Employer employer, AuditLogEntry auditEntry) {}
    }

    private static class StubComplianceCaseRepository implements ComplianceCaseRepository {
        final List<ComplianceCase> cases = new ArrayList<>();
        // Back-reference wired during setUp to simulate transactional side-effects
        StubAuditLogRepository auditRepoRef;

        @Override public void insert(ComplianceCase complianceCase) { cases.add(complianceCase); }
        @Override public void update(ComplianceCase complianceCase) {}
        @Override public ComplianceCase getByIdScoped(String id, String orgId) { return null; }
        @Override public LiveData<List<ComplianceCase>> getCases(String orgId) { return null; }
        @Override public LiveData<List<ComplianceCase>> getCasesByStatus(String orgId, String status) { return null; }
        @Override public void insertWithAuditLog(ComplianceCase complianceCase, AuditLogEntry auditEntry) {
            cases.add(complianceCase);
            if (auditRepoRef != null) auditRepoRef.log(auditEntry);
        }
    }

    private static class StubAuditLogRepository implements AuditLogRepository {
        final List<AuditLogEntry> logs = new ArrayList<>();

        @Override public void log(AuditLogEntry entry) { logs.add(entry); }
        @Override public LiveData<List<AuditLogEntry>> getLogsForCase(String caseId, String orgId) { return null; }
        @Override public LiveData<List<AuditLogEntry>> getAllLogs(String orgId) { return null; }
    }
}
