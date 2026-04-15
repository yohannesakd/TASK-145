package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.AuditLogRepository;
import com.roadrunner.dispatch.core.domain.repository.EmployerRepository;
import com.roadrunner.dispatch.core.util.AppLogger;
import java.util.UUID;

public class EnforceViolationUseCase {
    private final EmployerRepository employerRepository;
    private final AuditLogRepository auditLogRepository;

    public EnforceViolationUseCase(EmployerRepository employerRepository, AuditLogRepository auditLogRepository) {
        this.employerRepository = employerRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * @param action          One of: WARN, SUSPEND_7, SUSPEND_30, SUSPEND_365, TAKEDOWN, THROTTLE
     * @param actorId         ID of the actor performing the enforcement
     * @param caseId          Associated compliance case ID
     * @param orgId           Organisation scope
     * @param isZeroTolerance Whether zero-tolerance terms were involved (bypasses 2-warning requirement)
     * @param actorRole       Role of the actor; must be "COMPLIANCE_REVIEWER"
     */
    public Result<Employer> execute(String employerId, String action, String actorId,
                                      String caseId, String orgId, boolean isZeroTolerance,
                                      String actorRole) {
        AppLogger.info("EnforceViolation", "execute action=" + action + " employer=" + AppLogger.mask(employerId) + " actor=" + AppLogger.mask(actorId));
        if (!"COMPLIANCE_REVIEWER".equals(actorRole)) {
            AppLogger.warn("EnforceViolation", "Rejected: role=" + actorRole + " is not COMPLIANCE_REVIEWER");
            return Result.failure("Unauthorized: role must be COMPLIANCE_REVIEWER to enforce violations");
        }
        Employer employer = employerRepository.getByIdScoped(employerId, orgId);
        if (employer == null) {
            return Result.failure("Employer not found");
        }

        long now = System.currentTimeMillis();
        String auditAction;
        String auditDetails;
        Employer updated;

        switch (action) {
            case "WARN":
                int newWarningCount = employer.warningCount + 1;
                updated = new Employer(employer.id, employer.orgId, employer.legalName, employer.ein,
                    employer.streetAddress, employer.city, employer.state, employer.zipCode,
                    employer.status, newWarningCount, employer.suspendedUntil, employer.throttled);
                auditAction = "WARNING_ISSUED";
                auditDetails = "{\"warningNumber\":" + newWarningCount + "}";
                break;

            case "TAKEDOWN":
                updated = new Employer(employer.id, employer.orgId, employer.legalName, employer.ein,
                    employer.streetAddress, employer.city, employer.state, employer.zipCode,
                    "DEACTIVATED", employer.warningCount, employer.suspendedUntil, employer.throttled);
                auditAction = "TAKEDOWN";
                auditDetails = "{\"reason\":\"content_takedown\"}";
                break;

            case "SUSPEND_7":
            case "SUSPEND_30":
            case "SUSPEND_365":
                // Anti-harassment: need 2 warnings before suspension unless zero-tolerance
                if (!isZeroTolerance && employer.warningCount < 2) {
                    // Issue warning instead — atomically update employer + audit log.
                    int newWarnings = employer.warningCount + 1;
                    updated = new Employer(employer.id, employer.orgId, employer.legalName, employer.ein,
                        employer.streetAddress, employer.city, employer.state, employer.zipCode,
                        employer.status, newWarnings, employer.suspendedUntil, employer.throttled);
                    AuditLogEntry warnEntry = buildAuditEntry(orgId, actorId, "WARNING_ISSUED", "EMPLOYER",
                        employerId, caseId,
                        "{\"warningNumber\":" + newWarnings + ",\"requestedAction\":\"" + action + "\"}");
                    employerRepository.updateWithAuditLog(updated, warnEntry);

                    return Result.success(updated);
                }

                // Apply suspension
                int days = action.equals("SUSPEND_7") ? 7 : action.equals("SUSPEND_30") ? 30 : 365;
                long suspendedUntil = now + ((long) days * 24 * 60 * 60 * 1000);
                updated = new Employer(employer.id, employer.orgId, employer.legalName, employer.ein,
                    employer.streetAddress, employer.city, employer.state, employer.zipCode,
                    "SUSPENDED", employer.warningCount, suspendedUntil, employer.throttled);
                auditAction = "SUSPENSION_APPLIED";
                auditDetails = "{\"days\":" + days + ",\"zeroTolerance\":" + isZeroTolerance + ",\"until\":" + suspendedUntil + "}";
                break;

            case "THROTTLE":
                updated = new Employer(employer.id, employer.orgId, employer.legalName, employer.ein,
                    employer.streetAddress, employer.city, employer.state, employer.zipCode,
                    employer.status, employer.warningCount, employer.suspendedUntil, true);
                auditAction = "THROTTLE_APPLIED";
                auditDetails = "{\"visibility\":\"internal_only\"}";
                break;

            default:
                return Result.failure("Invalid action: " + action);
        }

        // Atomically update employer and write audit log in one transaction.
        AuditLogEntry auditEntry = buildAuditEntry(orgId, actorId, auditAction, "EMPLOYER",
            employerId, caseId, auditDetails);
        employerRepository.updateWithAuditLog(updated, auditEntry);

        return Result.success(updated);
    }

    /**
     * Issue a warning explicitly (standalone, not as part of a failed suspension).
     *
     * @param actorRole Role of the actor; must be "COMPLIANCE_REVIEWER"
     */
    public Result<Employer> issueWarning(String employerId, String actorId, String caseId,
                                          String orgId, String actorRole) {
        if (!"COMPLIANCE_REVIEWER".equals(actorRole)) {
            return Result.failure("Unauthorized: role must be COMPLIANCE_REVIEWER to issue warnings");
        }
        Employer employer = employerRepository.getByIdScoped(employerId, orgId);
        if (employer == null) return Result.failure("Employer not found");

        int newWarnings = employer.warningCount + 1;
        Employer updated = new Employer(employer.id, employer.orgId, employer.legalName, employer.ein,
            employer.streetAddress, employer.city, employer.state, employer.zipCode,
            employer.status, newWarnings, employer.suspendedUntil, employer.throttled);
        AuditLogEntry entry = buildAuditEntry(orgId, actorId, "WARNING_ISSUED", "EMPLOYER",
            employerId, caseId, "{\"warningNumber\":" + newWarnings + "}");
        employerRepository.updateWithAuditLog(updated, entry);

        return Result.success(updated);
    }

    /**
     * Remove throttle from an employer.
     *
     * @param actorRole Role of the actor; must be "COMPLIANCE_REVIEWER"
     */
    public Result<Employer> removeThrottle(String employerId, String actorId, String caseId,
                                            String orgId, String actorRole) {
        if (!"COMPLIANCE_REVIEWER".equals(actorRole)) {
            return Result.failure("Unauthorized: role must be COMPLIANCE_REVIEWER to remove throttle");
        }
        Employer employer = employerRepository.getByIdScoped(employerId, orgId);
        if (employer == null) return Result.failure("Employer not found");

        Employer updated = new Employer(employer.id, employer.orgId, employer.legalName, employer.ein,
            employer.streetAddress, employer.city, employer.state, employer.zipCode,
            employer.status, employer.warningCount, employer.suspendedUntil, false);
        AuditLogEntry entry = buildAuditEntry(orgId, actorId, "THROTTLE_REMOVED", "EMPLOYER",
            employerId, caseId, "{\"visibility\":\"public\"}");
        employerRepository.updateWithAuditLog(updated, entry);

        return Result.success(updated);
    }

    private AuditLogEntry buildAuditEntry(String orgId, String actorId, String action,
            String targetType, String targetId, String caseId, String details) {
        return new AuditLogEntry(
            UUID.randomUUID().toString(), orgId, actorId, action,
            targetType, targetId, details, caseId, System.currentTimeMillis()
        );
    }
}
