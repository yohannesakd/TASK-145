package com.roadrunner.dispatch.core.domain.model;

public class ComplianceCase {
    public final String id;
    public final String orgId;
    public final String employerId;
    public final String caseType;
    public final String status;
    public final String severity;
    public final String description;
    public final String createdBy;
    public final String assignedTo;

    public ComplianceCase(String id, String orgId, String employerId, String caseType,
                          String status, String severity, String description,
                          String createdBy, String assignedTo) {
        this.id = id;
        this.orgId = orgId;
        this.employerId = employerId;
        this.caseType = caseType;
        this.status = status;
        this.severity = severity;
        this.description = description;
        this.createdBy = createdBy;
        this.assignedTo = assignedTo;
    }
}
