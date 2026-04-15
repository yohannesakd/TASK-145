package com.roadrunner.dispatch.presentation.dispatch.taskdetail;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.roadrunner.dispatch.core.domain.model.MatchingWeights;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.ScoredWorker;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.core.domain.repository.TaskRepository;
import com.roadrunner.dispatch.core.domain.usecase.AcceptTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CompleteTaskUseCase;
import com.roadrunner.dispatch.core.domain.usecase.MatchTasksUseCase;
import com.roadrunner.dispatch.di.ServiceLocator;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the task-detail screen.
 *
 * <p>Serves two roles:
 * <ul>
 *   <li><strong>Dispatcher (ASSIGNED mode)</strong>: loads the task and a ranked list of
 *       available workers. The dispatcher can then call {@link #acceptTask(String, String, String)}
 *       to assign a worker.</li>
 *   <li><strong>Worker</strong>: loads the task and can call {@link #acceptTask(String, String, String)}
 *       (grab-order) or {@link #completeTask(String, String)} to advance the task state.</li>
 * </ul>
 */
public class TaskDetailViewModel extends ViewModel {

    private final TaskRepository taskRepository;
    private final AcceptTaskUseCase acceptTaskUseCase;
    private final CompleteTaskUseCase completeTaskUseCase;
    private final MatchTasksUseCase matchTasksUseCase;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String orgId;

    /** The task currently being viewed. */
    private final MutableLiveData<Task> task = new MutableLiveData<>();

    /**
     * Ranked workers for ASSIGNED-mode dispatcher view.
     * Empty list when not in dispatcher mode or no eligible workers.
     */
    private final MutableLiveData<List<ScoredWorker>> rankedWorkers = new MutableLiveData<>();

    /** Signals successful accept/complete; contains the updated task. */
    private final MutableLiveData<Task> actionResult = new MutableLiveData<>();

    /** Error from any operation. */
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public TaskDetailViewModel(TaskRepository taskRepository,
                                AcceptTaskUseCase acceptTaskUseCase,
                                CompleteTaskUseCase completeTaskUseCase,
                                MatchTasksUseCase matchTasksUseCase) {
        this.taskRepository = taskRepository;
        this.acceptTaskUseCase = acceptTaskUseCase;
        this.completeTaskUseCase = completeTaskUseCase;
        this.matchTasksUseCase = matchTasksUseCase;
    }

    // ── Exposed LiveData ──────────────────────────────────────────────────────

    public LiveData<Task> getTask() { return task; }
    public LiveData<List<ScoredWorker>> getRankedWorkers() { return rankedWorkers; }
    public LiveData<Task> getActionResult() { return actionResult; }
    public LiveData<String> getError() { return error; }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Load a task by ID and post it. Also triggers ranked-worker computation
     * when {@code loadRankedWorkers} is true (dispatcher ASSIGNED mode).
     */
    public void loadTask(String taskId, boolean loadRankedWorkers, String orgId,
                         MatchingWeights weights) {
        this.orgId = orgId;
        executor.execute(() -> {
            String effectiveOrgId = orgId;
            if (effectiveOrgId == null || effectiveOrgId.isEmpty()) {
                effectiveOrgId = ServiceLocator.getInstance().getSessionManager().getOrgId();
            }
            if (effectiveOrgId == null || effectiveOrgId.isEmpty()) {
                error.postValue("Organisation context required");
                return;
            }
            Task loaded = taskRepository.getByIdScoped(taskId, effectiveOrgId);
            if (loaded == null) {
                error.postValue("Task not found");
                return;
            }
            task.postValue(loaded);

            if (loadRankedWorkers && "ASSIGNED".equals(loaded.mode)) {
                MatchingWeights effective = weights != null ? weights : defaultWeights();
                List<ScoredWorker> scored =
                        matchTasksUseCase.rankWorkersForTask(loaded, effective, effectiveOrgId);
                rankedWorkers.postValue(scored);
            }
        });
    }

    /**
     * Dispatcher: assign {@code workerId} to {@code taskId} (ASSIGNED mode).
     * Worker: self-claim in grab-order mode.
     *
     * @param actorRole The role of the actor performing the action (e.g. "WORKER")
     */
    public void acceptTask(String taskId, String workerId, String actorRole) {
        executor.execute(() -> {
            Result<Task> result = acceptTaskUseCase.execute(taskId, workerId, actorRole,
                    orgId != null ? orgId : "");
            if (result.isSuccess()) {
                task.postValue(result.getData());
                actionResult.postValue(result.getData());
            } else {
                error.postValue(result.getFirstError());
            }
        });
    }

    /**
     * Worker: transition an ASSIGNED task to IN_PROGRESS.
     * Only the assigned worker may start the task.
     *
     * @param actorRole Role of the actor; must be "WORKER"
     */
    public void startTask(String taskId, String workerId, String actorRole) {
        executor.execute(() -> {
            if (!"WORKER".equals(actorRole)) {
                error.postValue("Unauthorized: only WORKER role can start tasks");
                return;
            }
            String effectiveOrgId = orgId;
            if (effectiveOrgId == null || effectiveOrgId.isEmpty()) {
                effectiveOrgId = ServiceLocator.getInstance().getSessionManager().getOrgId();
            }
            if (effectiveOrgId == null || effectiveOrgId.isEmpty()) {
                error.postValue("Organisation context required");
                return;
            }
            Task current = taskRepository.getByIdScoped(taskId, effectiveOrgId);
            if (current == null) { error.postValue("Task not found"); return; }
            if (!"ASSIGNED".equals(current.status)) {
                error.postValue("Task is not in ASSIGNED status");
                return;
            }
            if (!workerId.equals(current.assignedWorkerId)) {
                error.postValue("Task is not assigned to you");
                return;
            }
            Task started = new Task(
                current.id, current.orgId, current.title, current.description,
                "IN_PROGRESS", current.mode, current.priority, current.zoneId,
                current.windowStart, current.windowEnd, current.assignedWorkerId, current.createdBy
            );
            taskRepository.updateTask(started);
            task.postValue(started);
            actionResult.postValue(started);
        });
    }

    /**
     * Worker: mark the task as completed and record the reputation event.
     */
    public void completeTask(String taskId, String workerId, String actorRole) {
        executor.execute(() -> {
            Result<Task> result = completeTaskUseCase.execute(taskId, workerId, actorRole,
                    orgId != null ? orgId : "");
            if (result.isSuccess()) {
                task.postValue(result.getData());
                actionResult.postValue(result.getData());
            } else {
                error.postValue(result.getFirstError());
            }
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
