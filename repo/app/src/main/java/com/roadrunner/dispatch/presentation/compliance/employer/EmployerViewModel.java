package com.roadrunner.dispatch.presentation.compliance.employer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.roadrunner.dispatch.core.domain.model.ContentScanResult;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.EmployerRepository;
import com.roadrunner.dispatch.core.domain.usecase.ScanContentUseCase;
import com.roadrunner.dispatch.core.domain.usecase.VerifyEmployerUseCase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for employer management and content moderation.
 *
 * <p>Exposes the reactive employer list and provides:
 * <ul>
 *   <li>Employer verification (create/update with EIN and address validation)</li>
 *   <li>Content scanning for sensitive words before saving employer-submitted content</li>
 * </ul>
 */
public class EmployerViewModel extends ViewModel {

    private final EmployerRepository employerRepository;
    private final VerifyEmployerUseCase verifyEmployerUseCase;
    private final ScanContentUseCase scanContentUseCase;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Verified/saved employer — consumed by the UI after a successful verify. */
    private final MutableLiveData<Employer> savedEmployer = new MutableLiveData<>();

    /** Result of the most recent content scan. */
    private final MutableLiveData<ContentScanResult> scanResult = new MutableLiveData<>();

    /** Error from any operation. */
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public EmployerViewModel(EmployerRepository employerRepository,
                              VerifyEmployerUseCase verifyEmployerUseCase,
                              ScanContentUseCase scanContentUseCase) {
        this.employerRepository = employerRepository;
        this.verifyEmployerUseCase = verifyEmployerUseCase;
        this.scanContentUseCase = scanContentUseCase;
    }

    // ── Exposed LiveData ──────────────────────────────────────────────────────

    /**
     * Live list of all employers for the given org. Backed by Room.
     */
    public LiveData<List<Employer>> getEmployers(String orgId) {
        return employerRepository.getEmployers(orgId);
    }

    /**
     * Live list filtered by status (e.g., "VERIFIED", "SUSPENDED", "DEACTIVATED").
     */
    public LiveData<List<Employer>> getEmployersByStatus(String orgId, String status) {
        return employerRepository.getEmployersByStatus(orgId, status);
    }

    public LiveData<Employer> getSavedEmployer() { return savedEmployer; }
    public LiveData<ContentScanResult> getScanResult() { return scanResult; }
    public LiveData<String> getError() { return error; }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Validate and persist (insert or update) an employer record.
     * Posts to {@link #getSavedEmployer()} on success or {@link #getError()} on failure.
     *
     * @param employer  the employer to verify; set {@code id} to null for new records
     * @param actorRole the role of the actor performing this action
     */
    public void verifyEmployer(Employer employer, String actorRole) {
        executor.execute(() -> {
            Result<Employer> result = verifyEmployerUseCase.execute(employer, actorRole);
            if (result.isSuccess()) {
                savedEmployer.postValue(result.getData());
            } else {
                error.postValue(result.getFirstError());
            }
        });
    }

    /**
     * Scan arbitrary text for sensitive words. Posts the result to
     * {@link #getScanResult()} with one of: CLEAN, FLAGGED, ZERO_TOLERANCE.
     */
    public void scanContent(String text) {
        executor.execute(() -> {
            ContentScanResult result = scanContentUseCase.execute(text);
            scanResult.postValue(result);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
