package com.roadrunner.dispatch.core.domain.repository;

import androidx.lifecycle.LiveData;
import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.Employer;
import java.util.List;

public interface EmployerRepository {
    LiveData<List<Employer>> getEmployers(String orgId);
    List<Employer> getEmployersSync(String orgId);
    LiveData<List<Employer>> getEmployersByStatus(String orgId, String status);
    LiveData<List<Employer>> getEmployersFiltered(String orgId, boolean includeThrottled);
    Employer getByIdScoped(String id, String orgId);
    Employer getByEinScoped(String ein, String orgId);
    void insert(Employer employer);
    void update(Employer employer);

    /**
     * Atomically update the employer record and write the audit log entry in a
     * single database transaction. A crash between the two writes cannot leave the
     * employer in an updated state without a matching audit trail (or vice-versa).
     */
    void updateWithAuditLog(Employer employer, AuditLogEntry auditEntry);
}
