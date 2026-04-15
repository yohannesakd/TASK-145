package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.EmployerRepository;
import com.roadrunner.dispatch.core.domain.usecase.VerifyEmployerUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class VerifyEmployerUseCaseTest {

    private StubEmployerRepository employerRepo;
    private VerifyEmployerUseCase useCase;

    @Before
    public void setUp() {
        employerRepo = new StubEmployerRepository();
        useCase = new VerifyEmployerUseCase(employerRepo);
    }

    // -----------------------------------------------------------------------
    // Helper: build a fully valid employer
    // -----------------------------------------------------------------------

    private Employer validEmployer() {
        return new Employer(null, "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "CA", "12345",
                "PENDING", 0, 0L, false);
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    public void validEmployer_success_statusVerified() {
        Result<Employer> result = useCase.execute(validEmployer(), "ADMIN");
        assertTrue(result.isSuccess());
        assertEquals("VERIFIED", result.getData().status);
    }

    @Test
    public void validEmployer_insertsIntoRepository() {
        useCase.execute(validEmployer(), "ADMIN");
        assertFalse("Employer should be inserted", employerRepo.byEin.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Legal name validation
    // -----------------------------------------------------------------------

    @Test
    public void missingLegalName_failure() {
        Employer e = new Employer(null, "org1", null, "12-3456789",
                "123 Main St", "Springfield", "CA", "12345",
                "PENDING", 0, 0L, false);
        Result<Employer> result = useCase.execute(e, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(err -> err.toLowerCase().contains("legal name")));
    }

    @Test
    public void emptyLegalName_failure() {
        Employer e = new Employer(null, "org1", "  ", "12-3456789",
                "123 Main St", "Springfield", "CA", "12345",
                "PENDING", 0, 0L, false);
        assertFalse(useCase.execute(e, "ADMIN").isSuccess());
    }

    // -----------------------------------------------------------------------
    // EIN validation
    // -----------------------------------------------------------------------

    @Test
    public void ein_validFormat_12dash3456789_success() {
        Result<Employer> result = useCase.execute(validEmployer(), "ADMIN");
        assertTrue(result.isSuccess());
    }

    @Test
    public void ein_noDash_123456789_failure() {
        Employer e = new Employer(null, "org1", "Acme", "123456789",
                "123 Main St", "Springfield", "CA", "12345",
                "PENDING", 0, 0L, false);
        Result<Employer> result = useCase.execute(e, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(err -> err.toUpperCase().contains("EIN")));
    }

    @Test
    public void ein_lettersInstead_AB_CDEFGHI_failure() {
        Employer e = new Employer(null, "org1", "Acme", "AB-CDEFGHI",
                "123 Main St", "Springfield", "CA", "12345",
                "PENDING", 0, 0L, false);
        assertFalse(useCase.execute(e, "ADMIN").isSuccess());
    }

    @Test
    public void ein_tooShort_12dash345678_failure() {
        // Only 6 digits after dash instead of 7
        Employer e = new Employer(null, "org1", "Acme", "12-345678",
                "123 Main St", "Springfield", "CA", "12345",
                "PENDING", 0, 0L, false);
        assertFalse(useCase.execute(e, "ADMIN").isSuccess());
    }

    @Test
    public void ein_null_failure() {
        Employer e = new Employer(null, "org1", "Acme", null,
                "123 Main St", "Springfield", "CA", "12345",
                "PENDING", 0, 0L, false);
        assertFalse(useCase.execute(e, "ADMIN").isSuccess());
    }

    // -----------------------------------------------------------------------
    // State validation
    // -----------------------------------------------------------------------

    @Test
    public void state_twoLetterCode_CA_success() {
        Result<Employer> result = useCase.execute(validEmployer(), "ADMIN");
        assertTrue(result.isSuccess());
    }

    @Test
    public void state_fullName_California_failure() {
        Employer e = new Employer(null, "org1", "Acme", "12-3456789",
                "123 Main St", "Springfield", "California", "12345",
                "PENDING", 0, 0L, false);
        Result<Employer> result = useCase.execute(e, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(err -> err.toLowerCase().contains("state") || err.toLowerCase().contains("territory")));
    }

    @Test
    public void state_lowercase_failure() {
        Employer e = new Employer(null, "org1", "Acme", "12-3456789",
                "123 Main St", "Springfield", "ca", "12345",
                "PENDING", 0, 0L, false);
        assertFalse(useCase.execute(e, "ADMIN").isSuccess());
    }

    // -----------------------------------------------------------------------
    // ZIP code validation
    // -----------------------------------------------------------------------

    @Test
    public void zip_fiveDigits_12345_success() {
        Result<Employer> result = useCase.execute(validEmployer(), "ADMIN");
        assertTrue(result.isSuccess());
    }

    @Test
    public void zip_fivePlusFour_12345dash6789_success() {
        Employer e = new Employer(null, "org1", "Acme", "12-3456789",
                "123 Main St", "Springfield", "CA", "12345-6789",
                "PENDING", 0, 0L, false);
        assertTrue(useCase.execute(e, "ADMIN").isSuccess());
    }

    @Test
    public void zip_fourDigits_1234_failure() {
        Employer e = new Employer(null, "org1", "Acme", "12-3456789",
                "123 Main St", "Springfield", "CA", "1234",
                "PENDING", 0, 0L, false);
        Result<Employer> result = useCase.execute(e, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(err -> err.toUpperCase().contains("ZIP")));
    }

    @Test
    public void zip_null_failure() {
        Employer e = new Employer(null, "org1", "Acme", "12-3456789",
                "123 Main St", "Springfield", "CA", null,
                "PENDING", 0, 0L, false);
        assertFalse(useCase.execute(e, "ADMIN").isSuccess());
    }

    // -----------------------------------------------------------------------
    // Duplicate EIN
    // -----------------------------------------------------------------------

    @Test
    public void duplicateEin_failure() {
        // Insert an existing employer with same EIN
        Employer existing = new Employer("existing-id", "org1", "Existing Corp", "12-3456789",
                "456 Elm St", "Shelbyville", "NY", "67890",
                "VERIFIED", 0, 0L, false);
        employerRepo.byEin.put("12-3456789", existing);

        // Try to add a new employer with the same EIN
        Result<Employer> result = useCase.execute(validEmployer(), "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(err -> err.toLowerCase().contains("already exists")));
    }

    @Test
    public void updateExistingEmployer_sameEin_noDuplicateError() {
        // Updating an existing employer (id not null/empty) should skip duplicate check
        Employer toUpdate = new Employer("existing-id", "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "CA", "12345",
                "PENDING", 0, 0L, false);
        // Pre-populate repository with the same EIN
        employerRepo.byEin.put("12-3456789", toUpdate);
        employerRepo.byId.put("existing-id", toUpdate);

        Result<Employer> result = useCase.execute(toUpdate, "ADMIN");
        assertTrue("Update should not fail with duplicate EIN for same record", result.isSuccess());
    }

    @Test
    public void workerRole_rejected() {
        Employer emp = new Employer(null, "org1", "Acme Corp", "12-3456789",
            "123 Main St", "Springfield", "CA", "12345", null, 0, 0L, false);
        Result<Employer> result = useCase.execute(emp, "WORKER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("unauthorized")
                || result.getFirstError().toLowerCase().contains("role")
                || result.getFirstError().toLowerCase().contains("only"));
    }

    @Test
    public void nullOrgId_failure() {
        Employer emp = new Employer(null, null, "Acme Corp", "12-3456789",
            "123 Main St", "Springfield", "CA", "12345", null, 0, 0L, false);
        Result<Employer> result = useCase.execute(emp, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("organisation"));
    }

    @Test
    public void suspendedEmployer_preservesSuspendedStatus() {
        long futureTime = System.currentTimeMillis() + 86_400_000L;
        Employer suspended = new Employer("emp-s", "org1", "Acme Corp", "12-3456789",
            "123 Main St", "Springfield", "CA", "12345", "SUSPENDED", 0, futureTime, false);
        employerRepo.byId.put("emp-s", suspended);
        employerRepo.byEin.put("12-3456789", suspended);
        Result<Employer> result = useCase.execute(suspended, "ADMIN");
        assertTrue(result.isSuccess());
        assertEquals("SUSPENDED", result.getData().status);
    }

    @Test
    public void deactivatedEmployer_preservesDeactivatedStatus() {
        Employer deactivated = new Employer("emp-d", "org1", "Acme Corp", "22-3456789",
            "123 Main St", "Springfield", "CA", "12345", "DEACTIVATED", 0, 0L, false);
        employerRepo.byId.put("emp-d", deactivated);
        employerRepo.byEin.put("22-3456789", deactivated);
        Result<Employer> result = useCase.execute(deactivated, "ADMIN");
        assertTrue(result.isSuccess());
        assertEquals("DEACTIVATED", result.getData().status);
    }

    // -----------------------------------------------------------------------
    // Stub implementation
    // -----------------------------------------------------------------------

    private static class StubEmployerRepository implements EmployerRepository {
        final Map<String, Employer> byId = new HashMap<>();
        final Map<String, Employer> byEin = new HashMap<>();

        @Override
        public Employer getByIdScoped(String id, String orgId) { return byId.get(id); }

        @Override
        public Employer getByEinScoped(String ein, String orgId) { return byEin.get(ein); }

        @Override
        public void insert(Employer employer) {
            byId.put(employer.id, employer);
            byEin.put(employer.ein, employer);
        }

        @Override
        public void update(Employer employer) {
            byId.put(employer.id, employer);
            byEin.put(employer.ein, employer);
        }

        @Override
        public void updateWithAuditLog(Employer employer, AuditLogEntry auditEntry) {
            byId.put(employer.id, employer);
            byEin.put(employer.ein, employer);
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
}
