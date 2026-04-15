package com.roadrunner.dispatch.infrastructure.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.repository.EmployerRepository;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.AuditLogDao;
import com.roadrunner.dispatch.infrastructure.db.dao.EmployerDao;
import com.roadrunner.dispatch.infrastructure.db.entity.AuditLogEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.EmployerEntity;

import java.util.ArrayList;
import java.util.List;

public class EmployerRepositoryImpl implements EmployerRepository {

    private final AppDatabase db;
    private final EmployerDao employerDao;
    private final AuditLogDao auditLogDao;

    public EmployerRepositoryImpl(AppDatabase db, EmployerDao employerDao) {
        this.db = db;
        this.employerDao = employerDao;
        this.auditLogDao = db.auditLogDao();
    }

    @Override
    public LiveData<List<Employer>> getEmployers(String orgId) {
        return Transformations.map(
            employerDao.getEmployers(orgId),
            entities -> {
                List<Employer> result = new ArrayList<>();
                if (entities != null) {
                    for (EmployerEntity e : entities) {
                        result.add(toModel(e));
                    }
                }
                return result;
            }
        );
    }

    @Override
    public List<Employer> getEmployersSync(String orgId) {
        List<EmployerEntity> entities = employerDao.getEmployersSync(orgId);
        List<Employer> result = new ArrayList<>();
        if (entities != null) {
            for (EmployerEntity e : entities) {
                result.add(toModel(e));
            }
        }
        return result;
    }

    @Override
    public LiveData<List<Employer>> getEmployersByStatus(String orgId, String status) {
        return Transformations.map(
            employerDao.getEmployersByStatus(orgId, status),
            entities -> {
                List<Employer> result = new ArrayList<>();
                if (entities != null) {
                    for (EmployerEntity e : entities) {
                        result.add(toModel(e));
                    }
                }
                return result;
            }
        );
    }

    @Override
    public LiveData<List<Employer>> getEmployersFiltered(String orgId, boolean includeThrottled) {
        return Transformations.map(
            employerDao.getEmployersFilterThrottled(orgId, includeThrottled),
            entities -> {
                List<Employer> result = new ArrayList<>();
                if (entities != null) {
                    for (EmployerEntity e : entities) {
                        result.add(toModel(e));
                    }
                }
                return result;
            }
        );
    }

    @Override
    public Employer getByIdScoped(String id, String orgId) {
        EmployerEntity entity = employerDao.findByIdAndOrg(id, orgId);
        return entity != null ? toModel(entity) : null;
    }

    @Override
    public Employer getByEinScoped(String ein, String orgId) {
        EmployerEntity entity = employerDao.findByEinAndOrg(ein, orgId);
        return entity != null ? toModel(entity) : null;
    }

    @Override
    public void insert(Employer employer) {
        employerDao.insert(toEntity(employer));
    }

    @Override
    public void update(Employer employer) {
        employerDao.update(toEntity(employer));
    }

    /**
     * Atomically update the employer record and write the audit log entry in one
     * transaction. A crash between the two writes cannot leave the employer updated
     * without a matching audit trail (or vice-versa).
     */
    @Override
    public void updateWithAuditLog(Employer employer, AuditLogEntry auditEntry) {
        EmployerEntity employerEntity = toEntity(employer);
        AuditLogEntity logEntity = new AuditLogEntity(
                auditEntry.id, auditEntry.orgId, auditEntry.actorId, auditEntry.action,
                auditEntry.targetType, auditEntry.targetId, auditEntry.details,
                auditEntry.caseId, auditEntry.createdAt
        );
        db.runInTransaction(() -> {
            employerDao.update(employerEntity);
            auditLogDao.insert(logEntity);
        });
    }

    // --- Mapping ---

    private Employer toModel(EmployerEntity e) {
        return new Employer(
            e.id, e.orgId, e.legalName, e.ein,
            e.streetAddress, e.city, e.state, e.zipCode,
            e.status, e.warningCount, e.suspendedUntil, e.throttled
        );
    }

    private EmployerEntity toEntity(Employer m) {
        long now = System.currentTimeMillis();
        return new EmployerEntity(
            m.id, m.orgId, m.legalName, m.ein,
            m.streetAddress, m.city, m.state, m.zipCode,
            m.status, m.warningCount, m.suspendedUntil, m.throttled,
            now, now
        );
    }
}
