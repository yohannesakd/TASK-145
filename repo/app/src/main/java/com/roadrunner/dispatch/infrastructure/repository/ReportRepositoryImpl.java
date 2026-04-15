package com.roadrunner.dispatch.infrastructure.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.roadrunner.dispatch.core.domain.model.Report;
import com.roadrunner.dispatch.core.domain.repository.ReportRepository;
import com.roadrunner.dispatch.infrastructure.db.dao.ReportDao;
import com.roadrunner.dispatch.infrastructure.db.entity.ReportEntity;

import java.util.ArrayList;
import java.util.List;

public class ReportRepositoryImpl implements ReportRepository {

    private final ReportDao reportDao;

    public ReportRepositoryImpl(ReportDao reportDao) {
        this.reportDao = reportDao;
    }

    @Override
    public void fileReport(Report report) {
        reportDao.insert(toEntity(report));
    }

    @Override
    public Report getByIdScoped(String id, String orgId) {
        ReportEntity entity = reportDao.findByIdAndOrg(id, orgId);
        return entity != null ? toModel(entity) : null;
    }

    @Override
    public LiveData<List<Report>> getReportsForCase(String caseId, String orgId) {
        return Transformations.map(
            reportDao.getReportsForCase(caseId, orgId),
            entities -> {
                List<Report> result = new ArrayList<>();
                if (entities != null) {
                    for (ReportEntity e : entities) {
                        result.add(toModel(e));
                    }
                }
                return result;
            }
        );
    }

    @Override
    public LiveData<List<Report>> getAllReports(String orgId) {
        return Transformations.map(
            reportDao.getReportsForOrg(orgId),
            entities -> {
                List<Report> result = new ArrayList<>();
                if (entities != null) {
                    for (ReportEntity e : entities) {
                        result.add(toModel(e));
                    }
                }
                return result;
            }
        );
    }

    @Override
    public void update(Report report) {
        reportDao.update(toEntity(report));
    }

    // --- Mapping ---

    private Report toModel(ReportEntity e) {
        return new Report(
            e.id, e.orgId, e.caseId, e.reportedBy,
            e.targetType, e.targetId, e.description,
            e.evidenceUri, e.evidenceHash, e.status
        );
    }

    private ReportEntity toEntity(Report m) {
        return new ReportEntity(
            m.id, m.orgId, m.caseId, m.reportedBy,
            m.targetType, m.targetId, m.description,
            m.evidenceUri, m.evidenceHash, m.status,
            System.currentTimeMillis()
        );
    }
}
