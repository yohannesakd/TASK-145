package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.EmployerRepository;
import com.roadrunner.dispatch.core.domain.usecase.VerifyEmployerUseCase;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for import-path validation logic:
 * 1. SHA-256 fingerprint computation and comparison
 * 2. Employer validation via VerifyEmployerUseCase during import
 * 3. Skip/count behaviour for invalid records
 */
public class ImportValidationTest {

    private StubEmployerRepository employerRepo;
    private VerifyEmployerUseCase verifyUseCase;

    @Before
    public void setUp() {
        employerRepo = new StubEmployerRepository();
        verifyUseCase = new VerifyEmployerUseCase(employerRepo);
    }

    // -----------------------------------------------------------------------
    // SHA-256 fingerprint tests
    // -----------------------------------------------------------------------

    @Test
    public void sha256_sameInput_producesSameHash() throws Exception {
        String data = "{\"format\":\"roadrunner_v1\",\"orgId\":\"org1\"}";
        String hash1 = sha256(data);
        String hash2 = sha256(data);
        assertNotNull(hash1);
        assertEquals(hash1, hash2);
    }

    @Test
    public void sha256_differentInput_producesDifferentHash() throws Exception {
        String hash1 = sha256("{\"format\":\"roadrunner_v1\"}");
        String hash2 = sha256("{\"format\":\"roadrunner_v2\"}");
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotEquals(hash1, hash2);
    }

    @Test
    public void sha256_hashIsHex64Chars() throws Exception {
        String hash = sha256("test data");
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue("Must be lowercase hex", hash.matches("^[0-9a-f]{64}$"));
    }

    @Test
    public void sha256_tamperedPayload_mismatch() throws Exception {
        String original = "{\"format\":\"roadrunner_v1\",\"products\":[]}";
        String originalHash = sha256(original);

        // Simulating tampering
        String tampered = "{\"format\":\"roadrunner_v1\",\"products\":[{\"name\":\"injected\"}]}";
        String tamperedHash = sha256(tampered);

        assertNotEquals("Tampered payload must produce different hash", originalHash, tamperedHash);
    }

    // -----------------------------------------------------------------------
    // Employer import validation via VerifyEmployerUseCase
    // -----------------------------------------------------------------------

    @Test
    public void validEmployer_importSucceeds() {
        Employer candidate = employer("", "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62704");

        Result<Employer> result = verifyUseCase.execute(candidate, "ADMIN");

        assertTrue(result.isSuccess());
        assertEquals("VERIFIED", result.getData().status);
        assertEquals(1, employerRepo.insertedCount);
    }

    @Test
    public void invalidEin_importSkipped() {
        Employer candidate = employer("", "org1", "Bad Corp", "INVALID",
                "123 Main St", "Springfield", "IL", "62704");

        Result<Employer> result = verifyUseCase.execute(candidate, "ADMIN");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("EIN"));
        assertEquals(0, employerRepo.insertedCount);
    }

    @Test
    public void invalidState_importSkipped() {
        Employer candidate = employer("", "org1", "Bad Corp", "12-3456789",
                "123 Main St", "Springfield", "Illinois", "62704");

        Result<Employer> result = verifyUseCase.execute(candidate, "ADMIN");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("State"));
    }

    @Test
    public void invalidZip_importSkipped() {
        Employer candidate = employer("", "org1", "Bad Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "ABCDE");

        Result<Employer> result = verifyUseCase.execute(candidate, "ADMIN");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("ZIP"));
    }

    @Test
    public void missingLegalName_importSkipped() {
        Employer candidate = employer("", "org1", "", "12-3456789",
                "123 Main St", "Springfield", "IL", "62704");

        Result<Employer> result = verifyUseCase.execute(candidate, "ADMIN");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("Legal name"));
    }

    @Test
    public void duplicateEin_importSkipped() {
        // First import succeeds
        Employer first = employer("", "org1", "First Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62704");
        verifyUseCase.execute(first, "ADMIN");

        // Second import with same EIN is rejected
        Employer duplicate = employer("", "org1", "Second Corp", "12-3456789",
                "456 Oak Ave", "Chicago", "IL", "60601");
        Result<Employer> result = verifyUseCase.execute(duplicate, "ADMIN");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("EIN already exists"));
    }

    @Test
    public void multipleErrors_allReported() {
        Employer candidate = employer("", "org1", "", "BAD",
                "", "", "X", "ABC");

        Result<Employer> result = verifyUseCase.execute(candidate, "ADMIN");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().size() >= 4);
    }

    @Test
    public void nonAdminRole_cannotImportEmployers() {
        Employer candidate = employer("", "org1", "Acme Corp", "12-3456789",
                "123 Main St", "Springfield", "IL", "62704");

        Result<Employer> result = verifyUseCase.execute(candidate, "WORKER");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("Unauthorized"));
    }

    @Test
    public void complianceReviewer_canImportEmployers() {
        Employer candidate = employer("", "org1", "Acme Corp", "99-8765432",
                "789 Elm St", "Austin", "TX", "78701");

        Result<Employer> result = verifyUseCase.execute(candidate, "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
    }

    @Test
    public void zip5Plus4_accepted() {
        Employer candidate = employer("", "org1", "Acme Corp", "11-2223334",
                "123 Main St", "Springfield", "IL", "62704-1234");

        Result<Employer> result = verifyUseCase.execute(candidate, "ADMIN");

        assertTrue(result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Employer employer(String id, String orgId, String name, String ein,
            String street, String city, String state, String zip) {
        return new Employer(id, orgId, name, ein, street, city, state, zip,
                "PENDING", 0, 0, false);
    }

    /** Mirrors ImportExportFragment.computeSha256OfString */
    private static String sha256(String data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Stub
    // -----------------------------------------------------------------------

    private static class StubEmployerRepository implements EmployerRepository {
        int insertedCount = 0;
        private final Map<String, Employer> employers = new HashMap<>();

        @Override
        public Employer getByIdScoped(String id, String orgId) {
            Employer e = employers.get(id);
            if (e != null && !orgId.equals(e.orgId)) return null;
            return e;
        }

        @Override
        public Employer getByEinScoped(String ein, String orgId) {
            for (Employer e : employers.values()) {
                if (ein.equals(e.ein) && orgId.equals(e.orgId)) return e;
            }
            return null;
        }

        @Override
        public void insert(Employer employer) {
            employers.put(employer.id, employer);
            insertedCount++;
        }

        @Override
        public void update(Employer employer) {
            employers.put(employer.id, employer);
        }

        @Override
        public void updateWithAuditLog(Employer employer, com.roadrunner.dispatch.core.domain.model.AuditLogEntry auditEntry) {
            employers.put(employer.id, employer);
        }

        @Override
        public LiveData<List<Employer>> getEmployers(String orgId) { return null; }

        @Override
        public LiveData<List<Employer>> getEmployersByStatus(String orgId, String status) { return null; }

        @Override
        public LiveData<List<Employer>> getEmployersFiltered(String orgId, boolean includeThrottled) { return null; }

        @Override
        public List<Employer> getEmployersSync(String orgId) {
            List<Employer> result = new ArrayList<>();
            for (Employer e : employers.values()) {
                if (orgId.equals(e.orgId)) result.add(e);
            }
            return result;
        }
    }
}
