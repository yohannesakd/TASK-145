package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.Report;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.EmployerRepository;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;
import com.roadrunner.dispatch.core.domain.repository.ReportRepository;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class FileReportUseCase {
    private final ReportRepository reportRepository;
    private final EmployerRepository employerRepository;
    private final OrderRepository orderRepository;
    private final TaskRepository taskRepository;

    /**
     * Full constructor with target-entity validation support.
     *
     * @param reportRepository   repository for persisting reports (required)
     * @param employerRepository repository used to validate EMPLOYER targets; may be null to skip
     * @param orderRepository    repository used to validate ORDER targets; may be null to skip
     * @param taskRepository     repository used to validate TASK targets; may be null to skip
     */
    public FileReportUseCase(ReportRepository reportRepository,
                             EmployerRepository employerRepository,
                             OrderRepository orderRepository,
                             TaskRepository taskRepository) {
        this.reportRepository = reportRepository;
        this.employerRepository = employerRepository;
        this.orderRepository = orderRepository;
        this.taskRepository = taskRepository;
    }

    /**
     * Backward-compatible constructor; EMPLOYER, ORDER, and TASK target validation is disabled.
     */
    public FileReportUseCase(ReportRepository reportRepository,
                             EmployerRepository employerRepository,
                             OrderRepository orderRepository) {
        this(reportRepository, employerRepository, orderRepository, null);
    }

    /**
     * Backward-compatible constructor; all target validation is disabled.
     */
    public FileReportUseCase(ReportRepository reportRepository) {
        this(reportRepository, null, null, null);
    }

    /**
     * File a compliance report with optional evidence attachment.
     *
     * @param actorRole Role of the actor; must be "COMPLIANCE_REVIEWER" or "WORKER"
     */
    public Result<Report> execute(String orgId, String reportedBy, String targetType,
                                    String targetId, String description,
                                    String evidenceUri, String evidenceHash,
                                    String actorRole) {
        return execute(orgId, reportedBy, targetType, targetId, description,
                       evidenceUri, evidenceHash, actorRole, null);
    }

    /**
     * File a compliance report with optional evidence attachment and optional case link.
     *
     * @param actorRole Role of the actor; must be "COMPLIANCE_REVIEWER" or "WORKER"
     * @param caseId    optional compliance case ID to link this report to
     */
    public Result<Report> execute(String orgId, String reportedBy, String targetType,
                                    String targetId, String description,
                                    String evidenceUri, String evidenceHash,
                                    String actorRole, String caseId) {
        if (!"COMPLIANCE_REVIEWER".equals(actorRole) && !"WORKER".equals(actorRole)) {
            return Result.failure("Unauthorized: role must be COMPLIANCE_REVIEWER or WORKER to file reports");
        }

        List<String> errors = new ArrayList<>();
        if (targetType == null || targetType.trim().isEmpty()) errors.add("Target type is required");
        if (targetId == null || targetId.trim().isEmpty()) errors.add("Target ID is required");
        if (description == null || description.trim().isEmpty()) errors.add("Description is required");

        // Reject remote evidence URIs — only local/on-device URIs are permitted
        if (evidenceUri != null && !evidenceUri.trim().isEmpty()) {
            String lower = evidenceUri.trim().toLowerCase(Locale.ROOT);
            if (lower.startsWith("http://") || lower.startsWith("https://")
                    || lower.startsWith("ftp://") || lower.startsWith("ftps://")) {
                errors.add("Remote evidence URIs are not allowed. Only local file or content URIs are accepted.");
            } else if (!lower.startsWith("file://") && !lower.startsWith("content://")
                    && !lower.startsWith("/")) {
                errors.add("Unsupported evidence URI scheme. Only file:// and content:// URIs are accepted.");
            }
        }

        if (evidenceUri != null && !evidenceUri.trim().isEmpty()) {
            if (evidenceHash == null || evidenceHash.trim().isEmpty()) {
                errors.add("Evidence hash is required when evidence is attached");
            }
        }

        // Validate target entity exists within the org for known target types
        if (errors.isEmpty() && targetType != null && targetId != null) {
            if ("EMPLOYER".equals(targetType)) {
                if (employerRepository != null
                        && employerRepository.getByIdScoped(targetId, orgId) == null) {
                    errors.add("Target employer not found in this organisation");
                }
            } else if ("ORDER".equals(targetType)) {
                if (orderRepository != null
                        && orderRepository.getByIdScoped(targetId, orgId) == null) {
                    errors.add("Target order not found in this organisation");
                }
            } else if ("TASK".equals(targetType)) {
                if (taskRepository != null
                        && taskRepository.getByIdScoped(targetId, orgId) == null) {
                    errors.add("Target task not found in this organisation");
                }
            }
            // PRODUCT, USER: not validated here to avoid additional repository dependencies
        }

        if (!errors.isEmpty()) return Result.failure(errors);

        String linkedCaseId = (caseId != null && !caseId.trim().isEmpty()) ? caseId.trim() : null;
        Report report = new Report(
            UUID.randomUUID().toString(), orgId, linkedCaseId, reportedBy,
            targetType, targetId, description.trim(),
            evidenceUri, evidenceHash, "FILED"
        );
        reportRepository.fileReport(report);
        return Result.success(report);
    }
}
