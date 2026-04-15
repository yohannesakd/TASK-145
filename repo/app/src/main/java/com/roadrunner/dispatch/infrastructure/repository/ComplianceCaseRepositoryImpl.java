package com.roadrunner.dispatch.infrastructure.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.ComplianceCase;
import com.roadrunner.dispatch.core.domain.repository.ComplianceCaseRepository;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.AuditLogDao;
import com.roadrunner.dispatch.infrastructure.db.dao.ComplianceCaseDao;
import com.roadrunner.dispatch.infrastructure.db.entity.AuditLogEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ComplianceCaseEntity;

import java.util.ArrayList;
import java.util.List;

public class ComplianceCaseRepositoryImpl implements ComplianceCaseRepository {

    private final AppDatabase db;
    private final ComplianceCaseDao caseDao;
    private final AuditLogDao auditLogDao;

    public ComplianceCaseRepositoryImpl(AppDatabase db, ComplianceCaseDao caseDao) {
        this.db = db;
        this.caseDao = caseDao;
        this.auditLogDao = db.auditLogDao();
    }

    @Override
    public LiveData<List<ComplianceCase>> getCases(String orgId) {
        return Transformations.map(
            caseDao.getCases(orgId),
            entities -> {
                List<ComplianceCase> result = new ArrayList<>();
                if (entities != null) {
                    for (ComplianceCaseEntity e : entities) {
                        result.add(toModel(e));
                    }
                }
                return result;
            }
        );
    }

    @Override
    public LiveData<List<ComplianceCase>> getCasesByStatus(String orgId, String status) {
        return Transformations.map(
            caseDao.getCasesByStatus(orgId, status),
            entities -> {
                List<ComplianceCase> result = new ArrayList<>();
                if (entities != null) {
                    for (ComplianceCaseEntity e : entities) {
                        result.add(toModel(e));
                    }
                }
                return result;
            }
        );
    }

    @Override
    public ComplianceCase getByIdScoped(String id, String orgId) {
        ComplianceCaseEntity entity = caseDao.findByIdAndOrg(id, orgId);
        return entity != null ? toModel(entity) : null;
    }

    @Override
    public void insert(ComplianceCase complianceCase) {
        caseDao.insert(toEntity(complianceCase));
    }

    @Override
    public void update(ComplianceCase complianceCase) {
        caseDao.update(toEntity(complianceCase));
    }

    /**
     * Atomically insert the compliance case and write the audit log entry in one
     * transaction. A crash between the two writes cannot leave the database in a
     * partially-written state.
     */
    @Override
    public void insertWithAuditLog(ComplianceCase complianceCase, AuditLogEntry auditEntry) {
        ComplianceCaseEntity caseEntity = toEntity(complianceCase);
        AuditLogEntity logEntity = new AuditLogEntity(
                auditEntry.id, auditEntry.orgId, auditEntry.actorId, auditEntry.action,
                auditEntry.targetType, auditEntry.targetId, auditEntry.details,
                auditEntry.caseId, auditEntry.createdAt
        );
        db.runInTransaction(() -> {
            caseDao.insert(caseEntity);
            auditLogDao.insert(logEntity);
        });
    }

    // --- Mapping ---

    private ComplianceCase toModel(ComplianceCaseEntity e) {
        return new ComplianceCase(
            e.id, e.orgId, e.employerId, e.caseType,
            e.status, e.severity, e.description,
            e.createdBy, e.assignedTo
        );
    }

    private ComplianceCaseEntity toEntity(ComplianceCase m) {
        long now = System.currentTimeMillis();
        return new ComplianceCaseEntity(
            m.id, m.orgId, m.employerId, m.caseType,
            m.status, m.severity, m.description,
            m.createdBy, m.assignedTo,
            now, now
        );
    }
}
