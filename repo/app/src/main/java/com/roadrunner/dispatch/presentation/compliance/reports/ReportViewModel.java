package com.roadrunner.dispatch.presentation.compliance.reports;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.roadrunner.dispatch.core.domain.model.Report;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.ReportRepository;
import com.roadrunner.dispatch.core.domain.usecase.FileReportUseCase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the compliance-report screen.
 *
 * <p>Allows filing new reports against any entity type and exposes
 * all reports for the organisation via a Room-backed LiveData stream.
 * Reports linked to a specific compliance case can also be observed.
 */
public class ReportViewModel extends ViewModel {

    private final ReportRepository reportRepository;
    private final FileReportUseCase fileReportUseCase;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** The most recently filed report — consumed by the UI after a successful file. */
    private final MutableLiveData<Report> filedReport = new MutableLiveData<>();

    /** Error from any operation. */
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public ReportViewModel(ReportRepository reportRepository,
                            FileReportUseCase fileReportUseCase) {
        this.reportRepository = reportRepository;
        this.fileReportUseCase = fileReportUseCase;
    }

    // ── Exposed LiveData ──────────────────────────────────────────────────────

    /**
     * All reports filed in the given org; backed by Room.
     */
    public LiveData<List<Report>> getReports(String orgId) {
        return reportRepository.getAllReports(orgId);
    }

    /**
     * Reports associated with a specific compliance case.
     */
    public LiveData<List<Report>> getReportsForCase(String caseId, String orgId) {
        return reportRepository.getReportsForCase(caseId, orgId);
    }

    public LiveData<Report> getFiledReport() { return filedReport; }
    public LiveData<String> getError() { return error; }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * File a new compliance report.
     *
     * @param orgId        the organisation scope
     * @param reportedBy   user ID of the person filing the report
     * @param targetType   the kind of entity being reported (e.g., "EMPLOYER", "WORKER")
     * @param targetId     ID of the entity being reported
     * @param description  mandatory description of the issue
     * @param evidenceUri  optional URI pointing to supporting evidence
     * @param evidenceHash optional SHA-256 hash of the evidence for tamper-detection
     */
    public void fileReport(String orgId, String reportedBy, String targetType,
                           String targetId, String description,
                           String evidenceUri, String evidenceHash, String actorRole) {
        fileReport(orgId, reportedBy, targetType, targetId, description,
                   evidenceUri, evidenceHash, actorRole, null);
    }

    /**
     * File a new compliance report, optionally linking it to a case.
     */
    public void fileReport(String orgId, String reportedBy, String targetType,
                           String targetId, String description,
                           String evidenceUri, String evidenceHash, String actorRole,
                           String caseId) {
        executor.execute(() -> {
            Result<Report> result = fileReportUseCase.execute(
                    orgId, reportedBy, targetType, targetId,
                    description, evidenceUri, evidenceHash, actorRole, caseId);
            if (result.isSuccess()) {
                filedReport.postValue(result.getData());
            } else {
                error.postValue(result.getFirstError());
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
