package com.roadrunner.dispatch.core.domain.model;

public class Report {
    public final String id;
    public final String orgId;
    public final String caseId;
    public final String reportedBy;
    public final String targetType;
    public final String targetId;
    public final String description;
    public final String evidenceUri;
    public final String evidenceHash;
    public final String status;

    public Report(String id, String orgId, String caseId, String reportedBy, String targetType,
                  String targetId, String description, String evidenceUri,
                  String evidenceHash, String status) {
        this.id = id;
        this.orgId = orgId;
        this.caseId = caseId;
        this.reportedBy = reportedBy;
        this.targetType = targetType;
        this.targetId = targetId;
        this.description = description;
        this.evidenceUri = evidenceUri;
        this.evidenceHash = evidenceHash;
        this.status = status;
    }
}
