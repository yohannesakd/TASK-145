package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import com.roadrunner.dispatch.core.domain.model.Report;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.repository.EmployerRepository;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;
import com.roadrunner.dispatch.core.domain.repository.ReportRepository;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.core.domain.usecase.FileReportUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class FileReportUseCaseTest {

    private StubReportRepository reportRepo;
    private StubEmployerRepository employerRepo;
    private StubOrderRepository orderRepo;
    private StubTaskRepository taskRepoForTarget;
    private FileReportUseCase useCase;
    private FileReportUseCase useCaseWithValidation;

    @Before
    public void setUp() {
        reportRepo   = new StubReportRepository();
        employerRepo = new StubEmployerRepository();
        orderRepo    = new StubOrderRepository();
        taskRepoForTarget = new StubTaskRepository();
        // Prime default known entities so pre-existing EMPLOYER/ORDER tests continue to pass
        employerRepo.knownId    = "emp-5";
        employerRepo.knownOrgId = "org1";
        orderRepo.knownId    = "order-default";
        orderRepo.knownOrgId = "org1";
        useCase = new FileReportUseCase(reportRepo, employerRepo, orderRepo);
        useCaseWithValidation = new FileReportUseCase(reportRepo, employerRepo, orderRepo);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void validReport_success_statusFiled() {
        Result<Report> result = useCase.execute(
                "org1", "user1", "WORKER", "worker-42",
                "Observed safety violation", null, null, "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
        assertEquals("FILED", result.getData().status);
        assertNotNull(result.getData().id);
        assertFalse(result.getData().id.isEmpty());
    }

    @Test
    public void missingTargetType_failure() {
        Result<Report> result = useCase.execute(
                "org1", "user1", null, "target-1",
                "Description here", null, null, "COMPLIANCE_REVIEWER");

        assertFalse(result.isSuccess());
        boolean hasTargetTypeError = false;
        for (String err : result.getErrors()) {
            if (err.toLowerCase().contains("target type")) { hasTargetTypeError = true; break; }
        }
        assertTrue("Expected 'target type' error", hasTargetTypeError);
    }

    @Test
    public void missingTargetId_failure() {
        Result<Report> result = useCase.execute(
                "org1", "user1", "WORKER", "",
                "Description here", null, null, "COMPLIANCE_REVIEWER");

        assertFalse(result.isSuccess());
        boolean hasTargetIdError = false;
        for (String err : result.getErrors()) {
            if (err.toLowerCase().contains("target id")) { hasTargetIdError = true; break; }
        }
        assertTrue("Expected 'target id' error", hasTargetIdError);
    }

    @Test
    public void missingDescription_failure() {
        Result<Report> result = useCase.execute(
                "org1", "user1", "WORKER", "worker-42",
                "   ", null, null, "COMPLIANCE_REVIEWER");

        assertFalse(result.isSuccess());
        boolean hasDescError = false;
        for (String err : result.getErrors()) {
            if (err.toLowerCase().contains("description")) { hasDescError = true; break; }
        }
        assertTrue("Expected 'description' error", hasDescError);
    }

    @Test
    public void withLocalEvidenceUriAndHash_storedOnReport() {
        Result<Report> result = useCase.execute(
                "org1", "user1", "EMPLOYER", "emp-5",
                "Photo evidence attached",
                "file:///data/user/0/com.roadrunner.dispatch/files/evidence/photo.jpg",
                "sha256-abc123", "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
        assertEquals("file:///data/user/0/com.roadrunner.dispatch/files/evidence/photo.jpg",
                result.getData().evidenceUri);
        assertEquals("sha256-abc123", result.getData().evidenceHash);
    }

    @Test
    public void withContentUri_storedOnReport() {
        Result<Report> result = useCase.execute(
                "org1", "user1", "EMPLOYER", "emp-5",
                "Photo evidence attached",
                "content://com.android.providers.media/images/12345",
                "sha256-abc123", "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
        assertEquals("content://com.android.providers.media/images/12345",
                result.getData().evidenceUri);
    }

    @Test
    public void remoteHttpsUri_rejected() {
        Result<Report> result = useCase.execute(
                "org1", "user1", "EMPLOYER", "emp-5",
                "Photo evidence attached",
                "https://evidence.example.com/photo.jpg",
                "sha256-abc123", "COMPLIANCE_REVIEWER");

        assertFalse(result.isSuccess());
        boolean hasUriError = false;
        for (String err : result.getErrors()) {
            if (err.toLowerCase().contains("remote")) { hasUriError = true; break; }
        }
        assertTrue("Expected 'remote' URI error", hasUriError);
    }

    @Test
    public void remoteHttpUri_rejected() {
        Result<Report> result = useCase.execute(
                "org1", "user1", "EMPLOYER", "emp-5",
                "Photo evidence attached",
                "http://evil.example.com/exfiltrated.jpg",
                "sha256-abc123", "COMPLIANCE_REVIEWER");

        assertFalse(result.isSuccess());
        boolean hasUriError = false;
        for (String err : result.getErrors()) {
            if (err.toLowerCase().contains("remote")) { hasUriError = true; break; }
        }
        assertTrue("Expected 'remote' URI error", hasUriError);
    }

    @Test
    public void remoteFtpUri_rejected() {
        Result<Report> result = useCase.execute(
                "org1", "user1", "EMPLOYER", "emp-5",
                "FTP evidence",
                "ftp://files.example.com/evidence.pdf",
                "sha256-abc123", "COMPLIANCE_REVIEWER");

        assertFalse(result.isSuccess());
    }

    @Test
    public void absolutePathUri_accepted() {
        Result<Report> result = useCase.execute(
                "org1", "user1", "EMPLOYER", "emp-5",
                "Evidence from local storage",
                "/data/user/0/com.roadrunner.dispatch/files/evidence/photo.jpg",
                "sha256-abc123", "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
    }

    @Test
    public void workerRole_accepted() {
        Result<Report> result = useCase.execute(
                "org1", "user1", "WORKER", "worker-42",
                "Observed safety violation", null, null, "WORKER");

        assertTrue(result.isSuccess());
        assertEquals("FILED", result.getData().status);
    }

    @Test
    public void dispatcherRole_rejected() {
        Result<Report> result = useCase.execute(
                "org1", "user2", "WORKER", "worker-42",
                "Observed safety violation", null, null, "DISPATCHER");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role"));
    }

    @Test
    public void evidenceUriWithoutHash_failure() {
        Result<Report> result = useCase.execute("org1", "user1", "EMPLOYER", "emp1", "desc",
                "file:///data/evidence.pdf", null, "COMPLIANCE_REVIEWER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("hash"));
    }

    // -----------------------------------------------------------------------
    // Target-entity org-scoping validation tests
    // -----------------------------------------------------------------------

    @Test
    public void employerTarget_existsInOrg_success() {
        employerRepo.knownId = "emp-10";
        employerRepo.knownOrgId = "org1";

        Result<Report> result = useCase.execute(
                "org1", "user1", "EMPLOYER", "emp-10",
                "Compliance issue observed", null, null, "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
    }

    @Test
    public void employerTarget_notInOrg_failure() {
        // employerRepo returns null for unknown id/org combinations
        Result<Report> result = useCase.execute(
                "org1", "user1", "EMPLOYER", "emp-99",
                "Compliance issue observed", null, null, "COMPLIANCE_REVIEWER");

        assertFalse(result.isSuccess());
        boolean hasOrgError = false;
        for (String err : result.getErrors()) {
            if (err.toLowerCase().contains("employer") && err.toLowerCase().contains("organisation")) {
                hasOrgError = true;
                break;
            }
        }
        assertTrue("Expected employer not-found-in-org error", hasOrgError);
    }

    @Test
    public void orderTarget_existsInOrg_success() {
        orderRepo.knownId = "order-7";
        orderRepo.knownOrgId = "org1";

        Result<Report> result = useCase.execute(
                "org1", "user1", "ORDER", "order-7",
                "Fraudulent order detected", null, null, "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
    }

    @Test
    public void orderTarget_notInOrg_failure() {
        Result<Report> result = useCase.execute(
                "org1", "user1", "ORDER", "order-foreign",
                "Fraudulent order detected", null, null, "COMPLIANCE_REVIEWER");

        assertFalse(result.isSuccess());
        boolean hasOrgError = false;
        for (String err : result.getErrors()) {
            if (err.toLowerCase().contains("order") && err.toLowerCase().contains("organisation")) {
                hasOrgError = true;
                break;
            }
        }
        assertTrue("Expected order not-found-in-org error", hasOrgError);
    }

    @Test
    public void workerTarget_noValidation_success() {
        // USER/WORKER targets are not org-scoped — should succeed without any entity check
        Result<Report> result = useCase.execute(
                "org1", "user1", "USER", "user-external",
                "Misconduct reported", null, null, "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
    }

    @Test
    public void nullRepositories_skipValidation_success() {
        // When repos are null (backward-compat constructor), validation is skipped
        FileReportUseCase noValidationUseCase = new FileReportUseCase(reportRepo);

        Result<Report> result = noValidationUseCase.execute(
                "org1", "user1", "EMPLOYER", "any-id",
                "Report without validation", null, null, "COMPLIANCE_REVIEWER");

        assertTrue(result.isSuccess());
    }

    @Test
    public void unsupportedUriScheme_rejected() {
        Result<Report> result = useCaseWithValidation.execute("org1", "user1", "EMPLOYER", "emp-5",
            "Bad URI test", "custom://resource/path", "abc123hash", "COMPLIANCE_REVIEWER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("uri") || result.getFirstError().toLowerCase().contains("scheme") || result.getFirstError().toLowerCase().contains("evidence"));
    }

    @Test
    public void taskTarget_notInOrg_failure() {
        // Construct with 4-arg (including taskRepo)
        FileReportUseCase useCaseWithTaskRepo = new FileReportUseCase(reportRepo, employerRepo, orderRepo, taskRepoForTarget);
        Result<Report> result = useCaseWithTaskRepo.execute("org1", "user1", "TASK", "unknown-task",
            "Task target test", null, null, "COMPLIANCE_REVIEWER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("task") || result.getFirstError().toLowerCase().contains("not found"));
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubReportRepository implements ReportRepository {
        final List<Report> filed = new ArrayList<>();

        @Override public void fileReport(Report report) { filed.add(report); }
        @Override public Report getByIdScoped(String id, String orgId) { return null; }
        @Override public void update(Report report) {}
        @Override public LiveData<List<Report>> getReportsForCase(String caseId, String orgId) { return null; }
        @Override public LiveData<List<Report>> getAllReports(String orgId) { return null; }
    }

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

    private static class StubOrderRepository implements OrderRepository {
        String knownId;
        String knownOrgId;

        @Override
        public Order getByIdScoped(String id, String orgId) {
            if (id != null && id.equals(knownId) && orgId != null && orgId.equals(knownOrgId)) {
                return new Order(id, orgId, null, "cust-1", "store-1",
                        "PENDING", 0L, 0L, 0L, 0L, 0L,
                        null, null, 0L, false);
            }
            return null;
        }

        @Override public String createOrderFromCart(String orgId, String cartId, String customerId, String storeId, String createdBy, java.util.List<com.roadrunner.dispatch.core.domain.model.OrderItem> items) { return null; }
        @Override public Order findDraftByCartId(String cartId, String orgId) { return null; }
        @Override public LiveData<List<Order>> getOrders(String orgId) { return null; }
        @Override public LiveData<List<Order>> getOrdersByStatus(String orgId, String status) { return null; }
        @Override public LiveData<List<Order>> getOrdersByUser(String userId, String orgId) { return null; }
        @Override public List<com.roadrunner.dispatch.core.domain.model.OrderItem> getOrderItems(String orderId) { return null; }
        @Override public void deleteOrderItems(String orderId) {}
        @Override public void insertOrderItems(String orderId, java.util.List<com.roadrunner.dispatch.core.domain.model.OrderItem> items) {}
        @Override public void replaceOrderItems(String orderId, java.util.List<com.roadrunner.dispatch.core.domain.model.OrderItem> items) {}
        @Override public void updateOrder(Order order) {}
        @Override public void finalizeOrder(Order finalized, com.roadrunner.dispatch.core.domain.model.AuditLogEntry auditEntry) {}
        @Override public void applyDiscount(String orderId, String discountRuleId, long appliedAmountCents) {}
        @Override public void removeDiscounts(String orderId) {}
        @Override public List<String> getAppliedDiscountIds(String orderId) { return null; }
        @Override public List<com.roadrunner.dispatch.core.domain.model.DiscountRule> getDiscountRulesByIdScoped(List<String> ids, String orgId) { return null; }
        @Override public List<com.roadrunner.dispatch.core.domain.model.DiscountRule> getActiveDiscountRules(String orgId) { return null; }
        @Override public com.roadrunner.dispatch.core.domain.model.ShippingTemplate getShippingTemplateScoped(String id, String orgId) { return null; }
        @Override public List<com.roadrunner.dispatch.core.domain.model.ShippingTemplate> getShippingTemplates(String orgId) { return null; }
        @Override public void insertShippingTemplate(com.roadrunner.dispatch.core.domain.model.ShippingTemplate template) {}
        @Override public void insertDiscountRule(com.roadrunner.dispatch.core.domain.model.DiscountRule rule) {}
    }

    private static class StubTaskRepository implements TaskRepository {
        String knownId;
        String knownOrgId;

        @Override
        public Task getByIdScoped(String id, String orgId) {
            if (id != null && id.equals(knownId) && orgId != null && orgId.equals(knownOrgId)) {
                return new Task(id, orgId, "Test Task", "desc", "OPEN", "GRAB_ORDER",
                        "5", "zone1", 0L, 0L, null, "creator1");
            }
            return null;
        }

        @Override public void insert(Task task) {}
        @Override public void update(Task task) {}
        @Override public void updateTask(Task task) {}
        @Override public boolean hasAcceptance(String taskId, String workerId) { return false; }
        @Override public void insertAcceptance(String id, String taskId, String workerId, long acceptedAt) {}
        @Override public void claimTask(String acceptanceId, Task claimedTask, String workerId, long acceptedAt) {}
        @Override public void claimTaskWithSideEffects(String acceptanceId, Task claimedTask, String workerId,
                long acceptedAt, int workloadDelta, String orgId, AuditLogEntry auditEntry) {}
        @Override public void completeTaskWithSideEffects(Task completed, String workerId, int workloadDelta,
                String orgId, ReputationEvent reputationEvent, double newRepScore) {}
        @Override public List<Task> getOpenTasks(String orgId, String mode, long now) { return new ArrayList<>(); }
        @Override public List<Task> getWorkerActiveTasks(String orgId, String workerId) { return new ArrayList<>(); }
        @Override public LiveData<List<Task>> getWorkerActiveTasksLive(String orgId, String workerId) { return null; }
        @Override public LiveData<List<Task>> getTasks(String orgId) { return null; }
        @Override public LiveData<List<Task>> getTasksByStatus(String orgId, String status) { return null; }
    }
}
