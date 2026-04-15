package com.roadrunner.dispatch.infrastructure.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.repository.AuditLogRepository;
import com.roadrunner.dispatch.infrastructure.db.dao.AuditLogDao;
import com.roadrunner.dispatch.infrastructure.db.entity.AuditLogEntity;

import java.util.ArrayList;
import java.util.List;

public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogDao auditLogDao;

    public AuditLogRepositoryImpl(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    @Override
    public void log(AuditLogEntry entry) {
        auditLogDao.insert(toEntity(entry));
    }

    @Override
    public LiveData<List<AuditLogEntry>> getLogsForCase(String caseId, String orgId) {
        return Transformations.map(
            auditLogDao.getLogsForCase(caseId, orgId),
            entities -> {
                List<AuditLogEntry> result = new ArrayList<>();
                if (entities != null) {
                    for (AuditLogEntity e : entities) {
                        result.add(toModel(e));
                    }
                }
                return result;
            }
        );
    }

    @Override
    public LiveData<List<AuditLogEntry>> getAllLogs(String orgId) {
        return Transformations.map(
            auditLogDao.getLogsForOrg(orgId),
            entities -> {
                List<AuditLogEntry> result = new ArrayList<>();
                if (entities != null) {
                    for (AuditLogEntity e : entities) {
                        result.add(toModel(e));
                    }
                }
                return result;
            }
        );
    }

    // --- Mapping ---

    private AuditLogEntry toModel(AuditLogEntity e) {
        return new AuditLogEntry(
            e.id, e.orgId, e.actorId, e.action,
            e.targetType, e.targetId, e.details,
            e.caseId, e.createdAt
        );
    }

    private AuditLogEntity toEntity(AuditLogEntry m) {
        return new AuditLogEntity(
            m.id, m.orgId, m.actorId, m.action,
            m.targetType, m.targetId, m.details,
            m.caseId, m.createdAt
        );
    }
}
