package com.roadrunner.dispatch.core.domain.repository;

import androidx.lifecycle.LiveData;
import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import java.util.List;

public interface AuditLogRepository {
    void log(AuditLogEntry entry);
    LiveData<List<AuditLogEntry>> getLogsForCase(String caseId, String orgId);
    LiveData<List<AuditLogEntry>> getAllLogs(String orgId);
}
