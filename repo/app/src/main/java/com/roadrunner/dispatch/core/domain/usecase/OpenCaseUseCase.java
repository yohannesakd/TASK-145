package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ComplianceCase;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.AuditLogRepository;
import com.roadrunner.dispatch.core.domain.repository.ComplianceCaseRepository;
import com.roadrunner.dispatch.core.domain.repository.EmployerRepository;
import java.util.UUID;

public class OpenCaseUseCase {
    private final ComplianceCaseRepository caseRepository;
    private final AuditLogRepository auditLogRepository;
    private final EmployerRepository employerRepository;

    public OpenCaseUseCase(ComplianceCaseRepository caseRepository, AuditLogRepository auditLogRepository,
                           EmployerRepository employerRepository) {
        this.caseRepository = caseRepository;
        this.auditLogRepository = auditLogRepository;
        this.employerRepository = employerRepository;
    }

    /**
     * Open a new compliance case.
     *
     * @param actorRole Role of the actor; must be "COMPLIANCE_REVIEWER"
     */
    public Result<ComplianceCase> execute(String orgId, String employerId, String caseType,
                                            String severity, String description, String createdBy,
                                            String actorRole) {
        if (!"COMPLIANCE_REVIEWER".equals(actorRole)) {
            return Result.failure("Unauthorized: only compliance reviewers can open cases");
        }
        if (description == null || description.trim().isEmpty()) {
            return Result.failure("Case description is required");
        }

        if (employerId != null && !employerId.trim().isEmpty()) {
            Employer employer = employerRepository.getByIdScoped(employerId, orgId);
            if (employer == null) {
                return Result.failure("Employer not found in this organisation");
            }
        }

        ComplianceCase newCase = new ComplianceCase(
            UUID.randomUUID().toString(), orgId, employerId, caseType,
            "OPEN", severity, description.trim(), createdBy, null
        );

        AuditLogEntry logEntry = new AuditLogEntry(
            UUID.randomUUID().toString(), orgId, createdBy, "CASE_OPENED",
            "EMPLOYER", employerId != null ? employerId : "",
            "{\"caseType\":\"" + caseType + "\",\"severity\":\"" + severity + "\"}",
            newCase.id, System.currentTimeMillis()
        );

        // Atomically insert the case and audit log entry in one transaction.
        caseRepository.insertWithAuditLog(newCase, logEntry);

        return Result.success(newCase);
    }
}
