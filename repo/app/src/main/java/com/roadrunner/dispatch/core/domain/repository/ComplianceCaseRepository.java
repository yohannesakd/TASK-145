package com.roadrunner.dispatch.core.domain.repository;

import androidx.lifecycle.LiveData;
import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ComplianceCase;
import java.util.List;

public interface ComplianceCaseRepository {
    LiveData<List<ComplianceCase>> getCases(String orgId);
    LiveData<List<ComplianceCase>> getCasesByStatus(String orgId, String status);
    ComplianceCase getByIdScoped(String id, String orgId);
    void insert(ComplianceCase complianceCase);
    void update(ComplianceCase complianceCase);

    /**
     * Atomically insert the compliance case and write the audit log entry in a
     * single database transaction so a crash between the two writes cannot leave a
     * case record without an audit trail (or vice-versa).
     */
    void insertWithAuditLog(ComplianceCase complianceCase, AuditLogEntry auditEntry);
}
