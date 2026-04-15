package com.roadrunner.dispatch.presentation.compliance.cases;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ComplianceCase;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.AuditLogRepository;
import com.roadrunner.dispatch.core.domain.repository.ComplianceCaseRepository;
import com.roadrunner.dispatch.core.domain.usecase.EnforceViolationUseCase;
import com.roadrunner.dispatch.core.domain.usecase.OpenCaseUseCase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the compliance-case list and case-detail screens.
 *
 * <p>Provides:
 * <ul>
 *   <li>Live case lists (all, or filtered by status)</li>
 *   <li>Case opening with automatic audit log creation</li>
 *   <li>Enforcement actions (warn, suspend, throttle, takedown) against employers</li>
 *   <li>Audit log access for a specific case</li>
 * </ul>
 */
public class ComplianceCaseViewModel extends ViewModel {

    private final ComplianceCaseRepository caseRepository;
    private final AuditLogRepository auditLogRepository;
    private final OpenCaseUseCase openCaseUseCase;
    private final EnforceViolationUseCase enforceViolationUseCase;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Most recently opened case — consumed by navigation. */
    private final MutableLiveData<ComplianceCase> openedCase = new MutableLiveData<>();

    /** Result of the most recent enforcement action. */
    private final MutableLiveData<Employer> enforcementResult = new MutableLiveData<>();

    /** Error from any operation. */
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public ComplianceCaseViewModel(ComplianceCaseRepository caseRepository,
                                    AuditLogRepository auditLogRepository,
                                    OpenCaseUseCase openCaseUseCase,
                                    EnforceViolationUseCase enforceViolationUseCase) {
        this.caseRepository = caseRepository;
        this.auditLogRepository = auditLogRepository;
        this.openCaseUseCase = openCaseUseCase;
        this.enforceViolationUseCase = enforceViolationUseCase;
    }

    // ── Exposed LiveData ──────────────────────────────────────────────────────

    /** All cases for the given org; backed by Room. */
    public LiveData<List<ComplianceCase>> getCases(String orgId) {
        return caseRepository.getCases(orgId);
    }

    /** Cases filtered by status (e.g., "OPEN", "CLOSED"). */
    public LiveData<List<ComplianceCase>> getCasesByStatus(String orgId, String status) {
        return caseRepository.getCasesByStatus(orgId, status);
    }

    /** Audit log entries for a specific case. */
    public LiveData<List<AuditLogEntry>> getAuditLogsForCase(String caseId, String orgId) {
        return auditLogRepository.getLogsForCase(caseId, orgId);
    }

    /** All audit log entries in the org. */
    public LiveData<List<AuditLogEntry>> getAllAuditLogs(String orgId) {
        return auditLogRepository.getAllLogs(orgId);
    }

    public LiveData<ComplianceCase> getOpenedCase() { return openedCase; }
    public LiveData<Employer> getEnforcementResult() { return enforcementResult; }
    public LiveData<String> getError() { return error; }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Open a new compliance case and write the corresponding audit log entry.
     *
     * @param orgId       the organisation scope
     * @param employerId  the employer the case targets (may be null for general cases)
     * @param caseType    a short type tag (e.g., "CONTENT_VIOLATION", "WAGE_THEFT")
     * @param severity    one of "LOW", "MEDIUM", "HIGH", "CRITICAL"
     * @param description mandatory case description
     * @param createdBy   user ID of the compliance officer opening the case
     */
    public void openCase(String orgId, String employerId, String caseType,
                         String severity, String description, String createdBy, String actorRole) {
        executor.execute(() -> {
            Result<ComplianceCase> result = openCaseUseCase.execute(
                    orgId, employerId, caseType, severity, description, createdBy, actorRole);
            if (result.isSuccess()) {
                openedCase.postValue(result.getData());
            } else {
                error.postValue(result.getFirstError());
            }
        });
    }

    /**
     * Apply an enforcement action against an employer.
     *
     * @param employerId      the employer being acted on
     * @param action          one of WARN, SUSPEND_7, SUSPEND_30, SUSPEND_365, TAKEDOWN, THROTTLE
     * @param actorId         user ID of the compliance officer
     * @param caseId          the compliance case this action belongs to
     * @param orgId           organisation scope
     * @param isZeroTolerance whether zero-tolerance terms triggered this action
     * @param actorRole       role of the actor; must be "COMPLIANCE_REVIEWER"
     */
    public void enforceViolation(String employerId, String action, String actorId,
                                  String caseId, String orgId, boolean isZeroTolerance,
                                  String actorRole) {
        executor.execute(() -> {
            Result<Employer> result = enforceViolationUseCase.execute(
                    employerId, action, actorId, caseId, orgId, isZeroTolerance, actorRole);
            if (result.isSuccess()) {
                enforcementResult.postValue(result.getData());
            } else {
                error.postValue(result.getFirstError());
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
