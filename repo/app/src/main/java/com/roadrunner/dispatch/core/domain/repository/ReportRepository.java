package com.roadrunner.dispatch.core.domain.repository;

import androidx.lifecycle.LiveData;
import com.roadrunner.dispatch.core.domain.model.Report;
import java.util.List;

public interface ReportRepository {
    void fileReport(Report report);
    Report getByIdScoped(String id, String orgId);
    LiveData<List<Report>> getReportsForCase(String caseId, String orgId);
    LiveData<List<Report>> getAllReports(String orgId);
    void update(Report report);
}
