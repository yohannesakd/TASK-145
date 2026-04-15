package com.roadrunner.dispatch.presentation.dispatch.tasklist;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.roadrunner.dispatch.core.domain.model.ContentScanResult;
import com.roadrunner.dispatch.core.domain.model.MatchingWeights;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.core.domain.repository.WorkerRepository;
import com.roadrunner.dispatch.core.domain.usecase.CreateTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.MatchTasksUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ScanContentUseCase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the task-list screen.
 *
 * <p>Used by both the dispatcher role (full task list, create-task) and the
 * worker role (ranked grab-order tasks). The caller sets the session context
 * (orgId, current user/worker ID) and requests the appropriate data.
 *
 * <p>Dispatcher view: observe {@link #getTasks(String)} or
 * {@link #getTasksByStatus(String, String)}.
 * Worker/grab-order view: call {@link #loadRankedTasksForWorker(String, String, MatchingWeights)}
 * and observe {@link #rankedTasks}.
 */
public class TaskListViewModel extends ViewModel {

    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final CreateTaskUseCase createTaskUseCase;
    private final MatchTasksUseCase matchTasksUseCase;
    private final ScanContentUseCase scanContentUseCase;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Ranked grab-order tasks for the current worker (grab-order mode only). */
    private final MutableLiveData<List<MatchTasksUseCase.ScoredTask>> rankedTasks =
            new MutableLiveData<>();

    /** Result of the most recent content scan. */
    private final MutableLiveData<ContentScanResult> scanResult = new MutableLiveData<>();

    /** Most recently created task — consumed by the UI to navigate to detail. */
    private final MutableLiveData<Task> createdTask = new MutableLiveData<>();

    /** Error from any operation. */
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public TaskListViewModel(TaskRepository taskRepository,
                              WorkerRepository workerRepository,
                              CreateTaskUseCase createTaskUseCase,
                              MatchTasksUseCase matchTasksUseCase,
                              ScanContentUseCase scanContentUseCase) {
        this.taskRepository = taskRepository;
        this.workerRepository = workerRepository;
        this.createTaskUseCase = createTaskUseCase;
        this.matchTasksUseCase = matchTasksUseCase;
        this.scanContentUseCase = scanContentUseCase;
    }

    // ── Exposed LiveData ──────────────────────────────────────────────────────

    /**
     * All tasks for {@code orgId}. Backed by Room; updates automatically.
     */
    public LiveData<List<Task>> getTasks(String orgId) {
        return taskRepository.getTasks(orgId);
    }

    /**
     * Tasks for {@code orgId} filtered by {@code status} (e.g., "OPEN", "ASSIGNED").
     */
    public LiveData<List<Task>> getTasksByStatus(String orgId, String status) {
        return taskRepository.getTasksByStatus(orgId, status);
    }

    /** Worker's own active tasks (ASSIGNED + IN_PROGRESS). */
    private final MutableLiveData<List<Task>> myTasks = new MutableLiveData<>();

    public LiveData<List<Task>> getMyTasks() { return myTasks; }

    public LiveData<List<MatchTasksUseCase.ScoredTask>> getRankedTasks() { return rankedTasks; }
    public LiveData<Task> getCreatedTask() { return createdTask; }
    public LiveData<ContentScanResult> getScanResult() { return scanResult; }
    public LiveData<String> getError() { return error; }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Dispatcher: create a new task and post it to {@link #createdTask}.
     *
     * <p>Delegates to {@link #createTask(String, String, String, String, int, String, long, long, String, String, boolean)}
     * with {@code contentApproved=false}.
     *
     * @param actorRole Role of the creator (must be "DISPATCHER" or "ADMIN")
     */
    public void createTask(String orgId, String title, String description,
                           String mode, int priority, String zoneId,
                           long windowStart, long windowEnd, String createdBy,
                           String actorRole) {
        createTask(orgId, title, description, mode, priority, zoneId,
                windowStart, windowEnd, createdBy, actorRole, false);
    }

    /**
     * Dispatcher: create a new task and post it to {@link #createdTask}, with explicit
     * content-approval control.
     *
     * <p>Pass {@code contentApproved=true} when the user has already confirmed a FLAGGED-content
     * warning dialog; pass {@code false} (or use the shorter overload) for the default path where
     * FLAGGED content should be rejected with a "CONTENT_FLAGGED:" error.
     *
     * @param actorRole       Role of the creator (must be "DISPATCHER" or "ADMIN")
     * @param contentApproved Whether the user has pre-approved flagged content
     */
    public void createTask(String orgId, String title, String description,
                           String mode, int priority, String zoneId,
                           long windowStart, long windowEnd, String createdBy,
                           String actorRole, boolean contentApproved) {
        executor.execute(() -> {
            Result<Task> result = createTaskUseCase.execute(
                    orgId, title, description, mode, priority, zoneId,
                    windowStart, windowEnd, createdBy, actorRole, contentApproved);
            if (result.isSuccess()) {
                createdTask.postValue(result.getData());
            } else {
                error.postValue(result.getFirstError());
            }
        });
    }

    /**
     * Worker (grab-order mode): compute and post ranked open tasks.
     *
     * @param workerUserId the user ID associated with the logged-in worker
     * @param orgId        the organisation scope
     * @param weights      scoring weights; pass {@code null} for equal-weight defaults
     */
    public void loadRankedTasksForWorker(String workerUserId, String orgId,
                                          MatchingWeights weights) {
        executor.execute(() -> {
            Worker worker = workerRepository.getByUserIdScoped(workerUserId, orgId);
            if (worker == null) {
                error.postValue("Worker profile not found");
                return;
            }
            MatchingWeights effective = weights != null ? weights : defaultWeights();
            List<MatchTasksUseCase.ScoredTask> scored =
                    matchTasksUseCase.rankTasksForWorker(worker, effective, orgId);
            rankedTasks.postValue(scored);
        });
    }

    /**
     * Worker (grab-order mode): compute and post ranked open tasks using an
     * already-resolved worker ID. Avoids redundant userId→workerId resolution
     * when the caller has already performed it.
     *
     * @param workerId the worker's own ID (not the user ID)
     * @param orgId    the organisation scope
     * @param weights  scoring weights; pass {@code null} for equal-weight defaults
     */
    public void loadRankedTasksByWorkerId(String workerId, String orgId,
                                           MatchingWeights weights) {
        executor.execute(() -> {
            Worker worker = workerRepository.getByIdScoped(workerId, orgId);
            if (worker == null) {
                error.postValue("Worker profile not found");
                return;
            }
            MatchingWeights effective = weights != null ? weights : defaultWeights();
            List<MatchTasksUseCase.ScoredTask> scored =
                    matchTasksUseCase.rankTasksForWorker(worker, effective, orgId);
            rankedTasks.postValue(scored);
        });
    }

    /**
     * Worker: load assigned/in-progress tasks belonging to this worker.
     */
    public void loadMyTasks(String workerId, String orgId) {
        executor.execute(() -> {
            List<Task> tasks = taskRepository.getWorkerActiveTasks(orgId, workerId);
            myTasks.postValue(tasks);
        });
    }

    /**
     * Scan text for sensitive words. Posts result to {@link #getScanResult()}.
     */
    public void scanContent(String text) {
        executor.execute(() -> {
            ContentScanResult result = scanContentUseCase.execute(text);
            scanResult.postValue(result);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MatchingWeights defaultWeights() {
        android.content.SharedPreferences prefs =
            com.roadrunner.dispatch.RoadRunnerApp.getInstance()
                .getSharedPreferences("matching_weights", android.content.Context.MODE_PRIVATE);
        double w1 = Double.longBitsToDouble(prefs.getLong("w_time", Double.doubleToLongBits(0.3)));
        double w2 = Double.longBitsToDouble(prefs.getLong("w_load", Double.doubleToLongBits(0.25)));
        double w3 = Double.longBitsToDouble(prefs.getLong("w_rep",  Double.doubleToLongBits(0.25)));
        double w4 = Double.longBitsToDouble(prefs.getLong("w_zone", Double.doubleToLongBits(0.2)));
        return new MatchingWeights(w1, w2, w3, w4);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
