package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.ContentScanResult;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.core.domain.repository.ZoneRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CreateTaskUseCase {
    private final TaskRepository taskRepository;
    private final ZoneRepository zoneRepository;
    private final ScanContentUseCase scanContentUseCase;

    /**
     * Full constructor with content moderation support.
     *
     * @param taskRepository    repository for persisting tasks (required)
     * @param zoneRepository    repository used to validate zone existence (required)
     * @param scanContentUseCase use case for scanning title/description for prohibited terms;
     *                          may be null to skip content scanning
     */
    public CreateTaskUseCase(TaskRepository taskRepository, ZoneRepository zoneRepository,
                             ScanContentUseCase scanContentUseCase) {
        this.taskRepository = taskRepository;
        this.zoneRepository = zoneRepository;
        this.scanContentUseCase = scanContentUseCase;
    }

    /**
     * Backward-compatible constructor; content moderation scanning is disabled.
     */
    public CreateTaskUseCase(TaskRepository taskRepository, ZoneRepository zoneRepository) {
        this(taskRepository, zoneRepository, null);
    }

    /**
     * Validate inputs and create a new OPEN task.
     *
     * <p>Delegates to {@link #execute(String, String, String, String, int, String, long, long, String, String, boolean)}
     * with {@code contentApproved=false}. FLAGGED content will be rejected; callers that have
     * already obtained explicit user confirmation should use the overload that accepts
     * {@code contentApproved=true}.
     *
     * @param orgId       Organisation the task belongs to
     * @param title       Short title (required)
     * @param description Optional longer description
     * @param mode        GRAB_ORDER or ASSIGNED
     * @param priority    Numeric priority value (higher = more urgent)
     * @param zoneId      Zone the task is located in (must exist)
     * @param windowStart Epoch millis for the earliest acceptable start time
     * @param windowEnd   Epoch millis for the deadline (must be after windowStart)
     * @param createdBy   User ID of the creator
     * @param actorRole   Role of the creator; must be "DISPATCHER" or "ADMIN"
     * @return Result containing the newly created Task, or validation/lookup errors
     */
    public Result<Task> execute(String orgId, String title, String description,
                                String mode, int priority, String zoneId,
                                long windowStart, long windowEnd, String createdBy,
                                String actorRole) {
        return execute(orgId, title, description, mode, priority, zoneId,
                windowStart, windowEnd, createdBy, actorRole, false);
    }

    /**
     * Validate inputs and create a new OPEN task, with explicit content-approval control.
     *
     * @param orgId           Organisation the task belongs to
     * @param title           Short title (required)
     * @param description     Optional longer description
     * @param mode            GRAB_ORDER or ASSIGNED
     * @param priority        Numeric priority value (higher = more urgent)
     * @param zoneId          Zone the task is located in (must exist)
     * @param windowStart     Epoch millis for the earliest acceptable start time
     * @param windowEnd       Epoch millis for the deadline (must be after windowStart)
     * @param createdBy       User ID of the creator
     * @param actorRole       Role of the creator; must be "DISPATCHER" or "ADMIN"
     * @param contentApproved When {@code true}, FLAGGED content is allowed through (the user has
     *                        already confirmed the warning). When {@code false}, FLAGGED content
     *                        returns a distinguishable failure with the "CONTENT_FLAGGED:" prefix.
     *                        ZERO_TOLERANCE always blocks regardless of this flag.
     * @return Result containing the newly created Task, or validation/lookup errors
     */
    public Result<Task> execute(String orgId, String title, String description,
                                String mode, int priority, String zoneId,
                                long windowStart, long windowEnd, String createdBy,
                                String actorRole, boolean contentApproved) {
        if (!"DISPATCHER".equals(actorRole) && !"ADMIN".equals(actorRole)) {
            return Result.failure("Unauthorized: role must be DISPATCHER or ADMIN to create tasks");
        }
        List<String> errors = new ArrayList<>();

        if (title == null || title.trim().isEmpty()) {
            errors.add("Title is required");
        }
        if (mode == null || (!"GRAB_ORDER".equals(mode) && !"ASSIGNED".equals(mode))) {
            errors.add("Mode must be GRAB_ORDER or ASSIGNED");
        }
        if (zoneId == null || zoneId.trim().isEmpty()) {
            errors.add("Zone is required");
        }
        if (windowStart <= 0) {
            errors.add("Window start time is required");
        }
        if (windowEnd <= windowStart) {
            errors.add("Window end must be after window start");
        }

        if (!errors.isEmpty()) {
            return Result.failure(errors);
        }

        // Confirm zone exists within this org (orgId is required — reject if missing)
        if (orgId == null || orgId.isEmpty()) {
            return Result.failure("Organisation ID is required");
        }
        Zone zone = zoneRepository.getByIdScoped(zoneId, orgId);
        if (zone == null) {
            return Result.failure("Zone not found");
        }

        // Content moderation: scan title + description for prohibited terms
        if (scanContentUseCase != null) {
            String contentToScan = title.trim() + " " + (description != null ? description.trim() : "");
            ContentScanResult scanResult = scanContentUseCase.execute(contentToScan);
            if ("ZERO_TOLERANCE".equals(scanResult.status)) {
                return Result.failure("Task content contains prohibited terms");
            }
            if (!contentApproved && "FLAGGED".equals(scanResult.status)) {
                return Result.failure("CONTENT_FLAGGED: Task content contains flagged terms that require review");
            }
        }

        Task task = new Task(
                UUID.randomUUID().toString(),
                orgId,
                title.trim(),
                description != null ? description.trim() : "",
                "OPEN",
                mode,
                String.valueOf(priority),
                zoneId,
                windowStart,
                windowEnd,
                null,        // not yet assigned
                createdBy
        );
        taskRepository.insert(task);
        return Result.success(task);
    }
}
