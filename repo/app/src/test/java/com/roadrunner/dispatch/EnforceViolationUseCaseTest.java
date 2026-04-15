package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.AuditLogRepository;
import com.roadrunner.dispatch.core.domain.repository.EmployerRepository;
import com.roadrunner.dispatch.core.domain.usecase.EnforceViolationUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class EnforceViolationUseCaseTest {

    private StubEmployerRepository employerRepo;
    private StubAuditLogRepository auditRepo;
    private EnforceViolationUseCase useCase;

    @Before
    public void setUp() {
        employerRepo = new StubEmployerRepository();
        auditRepo = new StubAuditLogRepository();
        employerRepo.auditRepoRef = auditRepo;
        useCase = new EnforceViolationUseCase(employerRepo, auditRepo);
    }

    // -----------------------------------------------------------------------
    // Helper: create employer with given warning count
    // -----------------------------------------------------------------------

    private Employer employer(String id, int warningCount) {
        Employer e = new Employer(id, "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "CA", "12345",
                "ACTIVE", warningCount, 0L, false);
        employerRepo.insert(e);
        return e;
    }

    // -----------------------------------------------------------------------
    // Warning escalation tests
    // -----------------------------------------------------------------------

    @Test
    public void zeroWarnings_nonZeroTolerance_SUSPEND7_issuedAsWarningInstead() {
        employer("emp1", 0);
        Result<Employer> result = useCase.execute("emp1", "SUSPEND_7", "actor1", "case1", "org1", false, "COMPLIANCE_REVIEWER");
        assertTrue(result.isSuccess());
        Employer updated = result.getData();
        // Should have issued a warning instead
        assertEquals(1, updated.warningCount);
        // Status should remain ACTIVE (not SUSPENDED)
        assertEquals("ACTIVE", updated.status);
    }

    @Test
    public void oneWarning_nonZeroTolerance_SUSPEND7_issuedAsSecondWarning() {
        employer("emp2", 1);
        Result<Employer> result = useCase.execute("emp2", "SUSPEND_7", "actor1", "case1", "org1", false, "COMPLIANCE_REVIEWER");
        assertTrue(result.isSuccess());
        Employer updated = result.getData();
        assertEquals(2, updated.warningCount);
        assertEquals("ACTIVE", updated.status);
    }

    @Test
    public void twoWarnings_nonZeroTolerance_SUSPEND7_suspensionApplied() {
        employer("emp3", 2);
        Result<Employer> result = useCase.execute("emp3", "SUSPEND_7", "actor1", "case1", "org1", false, "COMPLIANCE_REVIEWER");
        assertTrue(result.isSuccess());
        Employer updated = result.getData();
        assertEquals("SUSPENDED", updated.status);
        // suspendedUntil should be about 7 days from now
        long sevenDayMs = 7L * 24 * 60 * 60 * 1000;
        long now = System.currentTimeMillis();
        assertTrue("suspendedUntil should be ~7 days ahead",
                updated.suspendedUntil > now + sevenDayMs - 5000 &&
                updated.suspendedUntil < now + sevenDayMs + 5000);
    }

    @Test
    public void zeroWarnings_zeroTolerance_SUSPEND7_immediateSuspension() {
        employer("emp4", 0);
        Result<Employer> result = useCase.execute("emp4", "SUSPEND_7", "actor1", "case1", "org1", true, "COMPLIANCE_REVIEWER");
        assertTrue(result.isSuccess());
        Employer updated = result.getData();
        assertEquals("SUSPENDED", updated.status);
    }

    @Test
    public void zeroWarnings_zeroTolerance_SUSPEND30_immediateThirtyDaySuspension() {
        employer("emp5", 0);
        Result<Employer> result = useCase.execute("emp5", "SUSPEND_30", "actor1", "case1", "org1", true, "COMPLIANCE_REVIEWER");
        assertTrue(result.isSuccess());
        assertEquals("SUSPENDED", result.getData().status);
        long thirtyDayMs = 30L * 24 * 60 * 60 * 1000;
        long now = System.currentTimeMillis();
        assertTrue(result.getData().suspendedUntil > now + thirtyDayMs - 5000);
    }

    // -----------------------------------------------------------------------
    // TAKEDOWN
    // -----------------------------------------------------------------------

    @Test
    public void takedown_statusDeactivated() {
        employer("emp6", 0);
        Result<Employer> result = useCase.execute("emp6", "TAKEDOWN", "actor1", "case1", "org1", false, "COMPLIANCE_REVIEWER");
        assertTrue(result.isSuccess());
        assertEquals("DEACTIVATED", result.getData().status);
    }

    // -----------------------------------------------------------------------
    // THROTTLE
    // -----------------------------------------------------------------------

    @Test
    public void throttle_throttledFlagSetTrue() {
        employer("emp7", 0);
        Result<Employer> result = useCase.execute("emp7", "THROTTLE", "actor1", "case1", "org1", false, "COMPLIANCE_REVIEWER");
        assertTrue(result.isSuccess());
        assertTrue("Throttled flag should be true", result.getData().throttled);
    }

    // -----------------------------------------------------------------------
    // Invalid action
    // -----------------------------------------------------------------------

    @Test
    public void invalidAction_failure() {
        employer("emp8", 0);
        Result<Employer> result = useCase.execute("emp8", "INVALID_OP", "actor1", "case1", "org1", false, "COMPLIANCE_REVIEWER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("invalid"));
    }

    // -----------------------------------------------------------------------
    // Employer not found
    // -----------------------------------------------------------------------

    @Test
    public void employerNotFound_failure() {
        Result<Employer> result = useCase.execute("nonexistent", "TAKEDOWN", "actor1", "case1", "org1", false, "COMPLIANCE_REVIEWER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("not found"));
    }

    // -----------------------------------------------------------------------
    // Audit log entries
    // -----------------------------------------------------------------------

    @Test
    public void takedown_auditLogCreated() {
        employer("emp9", 0);
        useCase.execute("emp9", "TAKEDOWN", "actor1", "case1", "org1", false, "COMPLIANCE_REVIEWER");
        assertFalse("Audit log should have an entry", auditRepo.logs.isEmpty());
        AuditLogEntry entry = auditRepo.logs.get(0);
        assertEquals("TAKEDOWN", entry.action);
        assertEquals("emp9", entry.targetId);
    }

    @Test
    public void suspend_auditLogCreated() {
        employer("emp10", 2);
        useCase.execute("emp10", "SUSPEND_7", "actor2", "case2", "org1", false, "COMPLIANCE_REVIEWER");
        assertFalse(auditRepo.logs.isEmpty());
        assertEquals("SUSPENSION_APPLIED", auditRepo.logs.get(0).action);
    }

    @Test
    public void warningIssuedInsteadOfSuspend_auditLogHasWarningAction() {
        employer("emp11", 0);
        useCase.execute("emp11", "SUSPEND_7", "actor3", "case3", "org1", false, "COMPLIANCE_REVIEWER");
        assertFalse(auditRepo.logs.isEmpty());
        assertEquals("WARNING_ISSUED", auditRepo.logs.get(0).action);
    }

    @Test
    public void throttle_auditLogCreated() {
        employer("emp12", 0);
        useCase.execute("emp12", "THROTTLE", "actor1", "case1", "org1", false, "COMPLIANCE_REVIEWER");
        assertFalse(auditRepo.logs.isEmpty());
        assertEquals("THROTTLE_APPLIED", auditRepo.logs.get(0).action);
    }

    @Test
    public void eachActionCreatesOneAuditEntry() {
        employer("emp13", 2);
        useCase.execute("emp13", "SUSPEND_7", "actor1", "case1", "org1", false, "COMPLIANCE_REVIEWER");
        assertEquals(1, auditRepo.logs.size());
    }

    // -----------------------------------------------------------------------
    // Role-based access control
    // -----------------------------------------------------------------------

    @Test
    public void workerRole_rejected() {
        employer("emp14", 0);
        Result<Employer> result = useCase.execute("emp14", "SUSPEND_7", "actor1", "case1", "org1", false, "WORKER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role"));
    }

    @Test
    public void adminRole_rejected() {
        employer("emp15", 0);
        Result<Employer> result = useCase.execute("emp15", "TAKEDOWN", "actor1", "case1", "org1", false, "ADMIN");
        assertFalse(result.isSuccess());
    }

    @Test
    public void dispatcherRole_rejected() {
        employer("emp16", 0);
        Result<Employer> result = useCase.execute("emp16", "THROTTLE", "actor1", "case1", "org1", false, "DISPATCHER");
        assertFalse(result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // issueWarning / removeThrottle role checks
    // -----------------------------------------------------------------------

    @Test
    public void issueWarning_complianceReviewer_success() {
        employer("empW1", 0);
        Result<Employer> result = useCase.issueWarning("empW1", "actor1", "case1", "org1", "COMPLIANCE_REVIEWER");
        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().warningCount);
    }

    @Test
    public void issueWarning_workerRole_rejected() {
        employer("empW2", 0);
        Result<Employer> result = useCase.issueWarning("empW2", "actor1", "case1", "org1", "WORKER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role"));
    }

    @Test
    public void issueWarning_adminRole_rejected() {
        employer("empW3", 0);
        Result<Employer> result = useCase.issueWarning("empW3", "actor1", "case1", "org1", "ADMIN");
        assertFalse(result.isSuccess());
    }

    @Test
    public void issueWarning_dispatcherRole_rejected() {
        employer("empW4", 0);
        Result<Employer> result = useCase.issueWarning("empW4", "actor1", "case1", "org1", "DISPATCHER");
        assertFalse(result.isSuccess());
    }

    @Test
    public void removeThrottle_complianceReviewer_success() {
        Employer e = new Employer("empT1", "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "CA", "12345",
                "ACTIVE", 0, 0L, true);
        employerRepo.insert(e);
        Result<Employer> result = useCase.removeThrottle("empT1", "actor1", "case1", "org1", "COMPLIANCE_REVIEWER");
        assertTrue(result.isSuccess());
        assertFalse("Throttle should be removed", result.getData().throttled);
    }

    @Test
    public void removeThrottle_workerRole_rejected() {
        employer("empT2", 0);
        Result<Employer> result = useCase.removeThrottle("empT2", "actor1", "case1", "org1", "WORKER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role"));
    }

    @Test
    public void removeThrottle_adminRole_rejected() {
        employer("empT3", 0);
        Result<Employer> result = useCase.removeThrottle("empT3", "actor1", "case1", "org1", "ADMIN");
        assertFalse(result.isSuccess());
    }

    @Test
    public void explicitWarnAction_incrementsWarningCount() {
        employerRepo.byId.put("empW", new Employer("empW", "org1", "Acme Corp", "12-3456789",
            "123 Main St", "Springfield", "CA", "12345", "ACTIVE", 0, 0L, false));
        Result<Employer> result = useCase.execute("empW", "WARN", "actor1", "case1", "org1", false, "COMPLIANCE_REVIEWER");
        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().warningCount);
    }

    @Test
    public void suspend365_zeroTolerance_appliesSuspension() {
        employerRepo.byId.put("emp365", new Employer("emp365", "org1", "Acme Corp", "12-3456789",
            "123 Main St", "Springfield", "CA", "12345", "ACTIVE", 0, 0L, false));
        Result<Employer> result = useCase.execute("emp365", "SUSPEND_365", "actor1", "case1", "org1", true, "COMPLIANCE_REVIEWER");
        assertTrue(result.isSuccess());
        assertEquals("SUSPENDED", result.getData().status);
        assertTrue(result.getData().suspendedUntil > System.currentTimeMillis() + 360L * 24 * 60 * 60 * 1000);
    }

    @Test
    public void issueWarning_employerNotFound_failure() {
        Result<Employer> result = useCase.issueWarning("nonexistent", "actor1", "case1", "org1", "COMPLIANCE_REVIEWER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("employer")
                || result.getFirstError().toLowerCase().contains("not found"));
    }

    @Test
    public void removeThrottle_employerNotFound_failure() {
        Result<Employer> result = useCase.removeThrottle("nonexistent", "actor1", "case1", "org1", "COMPLIANCE_REVIEWER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("employer")
                || result.getFirstError().toLowerCase().contains("not found"));
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubEmployerRepository implements EmployerRepository {
        final Map<String, Employer> byId = new HashMap<>();
        // Back-reference wired during setUp to simulate transactional side-effects
        StubAuditLogRepository auditRepoRef;

        @Override
        public Employer getByIdScoped(String id, String orgId) { return byId.get(id); }

        @Override
        public Employer getByEinScoped(String ein, String orgId) {
            for (Employer e : byId.values()) if (e.ein.equals(ein) && e.orgId.equals(orgId)) return e;
            return null;
        }

        @Override
        public void insert(Employer employer) { byId.put(employer.id, employer); }

        @Override
        public void update(Employer employer) { byId.put(employer.id, employer); }

        @Override
        public void updateWithAuditLog(Employer employer, AuditLogEntry auditEntry) {
            byId.put(employer.id, employer);
            if (auditRepoRef != null) auditRepoRef.log(auditEntry);
        }

        @Override
        public LiveData<List<Employer>> getEmployers(String orgId) { return null; }

        @Override
        public java.util.List<Employer> getEmployersSync(String orgId) { return new java.util.ArrayList<>(byId.values()); }

        @Override
        public LiveData<List<Employer>> getEmployersByStatus(String orgId, String status) { return null; }

        @Override
        public LiveData<List<Employer>> getEmployersFiltered(String orgId, boolean includeThrottled) { return null; }
    }

    private static class StubAuditLogRepository implements AuditLogRepository {
        final List<AuditLogEntry> logs = new ArrayList<>();

        @Override
        public void log(AuditLogEntry entry) { logs.add(entry); }

        @Override
        public LiveData<List<AuditLogEntry>> getLogsForCase(String caseId, String orgId) { return null; }

        @Override
        public LiveData<List<AuditLogEntry>> getAllLogs(String orgId) { return null; }
    }
}
