package com.roadrunner.dispatch.presentation.dispatch.taskdetail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Fragment for the task detail screen. Supports both dispatcher and worker roles.
 *
 * <p>Dispatcher (ASSIGNED mode): shows ranked workers + Assign button.
 * Worker (OPEN task): shows Claim button.
 * Worker (assigned to them): shows Start and Complete buttons.
 */
public class TaskDetailFragment extends Fragment {

    public static final String ARG_TASK_ID       = "task_id";
    public static final String ARG_IS_DISPATCHER = "is_dispatcher";
    public static final String ARG_ORG_ID        = "org_id";
    public static final String ARG_WORKER_ID     = "worker_id";

    private TaskDetailViewModel viewModel;
    private ScoredWorkerAdapter workerAdapter;

    // Views
    private TextView tvTitle;
    private TextView tvDescription;
    private Chip chipStatus;
    private TextView tvZone;
    private TextView tvTimeWindow;
    private TextView tvPriority;

    private MaterialCardView cardAssignedWorker;
    private TextView tvWorkerName;
    private Chip chipReputation;

    private MaterialCardView cardRankedWorkers;
    private RecyclerView recyclerRankedWorkers;

    private MaterialButton btnClaim;
    private MaterialButton btnStart;
    private MaterialButton btnComplete;
    private MaterialButton btnAssign;

    private ProgressBar progressBar;
    private TextView tvError;

    private String taskId;
    private boolean isDispatcher;
    private String orgId;
    private String workerId;

    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    // Track which worker is selected from the ranked list
    private String selectedWorkerId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            taskId       = getArguments().getString(ARG_TASK_ID, "");
            isDispatcher = getArguments().getBoolean(ARG_IS_DISPATCHER, false);
            orgId        = getArguments().getString(ARG_ORG_ID, "");
            workerId     = getArguments().getString(ARG_WORKER_ID, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_task_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupRankedWorkersRecycler();

        isDispatcher = RoleGuard.hasRole("DISPATCHER", "ADMIN");

        viewModel = new ViewModelProvider(this,
                new TaskDetailViewModelFactory(ServiceLocator.getInstance())
        ).get(TaskDetailViewModel.class);

        viewModel.getTask().observe(getViewLifecycleOwner(), this::renderTask);
        viewModel.getRankedWorkers().observe(getViewLifecycleOwner(), workers -> {
            if (workers != null && !workers.isEmpty()) {
                cardRankedWorkers.setVisibility(View.VISIBLE);
                workerAdapter.submitList(workers);
            }
        });
        viewModel.getActionResult().observe(getViewLifecycleOwner(), updatedTask -> {
            progressBar.setVisibility(View.GONE);
            if (updatedTask != null) {
                renderTask(updatedTask);
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            progressBar.setVisibility(View.GONE);
            if (error != null) {
                tvError.setVisibility(View.VISIBLE);
                tvError.setText(error);
            }
        });

        // Load task
        progressBar.setVisibility(View.VISIBLE);
        viewModel.loadTask(taskId, isDispatcher, orgId, null);
    }

    private void bindViews(View root) {
        tvTitle       = root.findViewById(R.id.tv_task_title);
        tvDescription = root.findViewById(R.id.tv_description);
        chipStatus    = root.findViewById(R.id.chip_status);
        tvZone        = root.findViewById(R.id.tv_zone);
        tvTimeWindow  = root.findViewById(R.id.tv_time_window);
        tvPriority    = root.findViewById(R.id.tv_priority);

        cardAssignedWorker = root.findViewById(R.id.card_assigned_worker);
        tvWorkerName       = root.findViewById(R.id.tv_worker_name);
        chipReputation     = root.findViewById(R.id.chip_reputation);

        cardRankedWorkers    = root.findViewById(R.id.card_ranked_workers);
        recyclerRankedWorkers = root.findViewById(R.id.recycler_ranked_workers);

        btnClaim    = root.findViewById(R.id.btn_claim);
        btnStart    = root.findViewById(R.id.btn_start);
        btnComplete = root.findViewById(R.id.btn_complete);
        btnAssign   = root.findViewById(R.id.btn_assign);

        progressBar = root.findViewById(R.id.progress_bar);
        tvError     = root.findViewById(R.id.tv_error);
    }

    private void setupRankedWorkersRecycler() {
        workerAdapter = new ScoredWorkerAdapter(scoredWorker -> {
            selectedWorkerId = scoredWorker.worker.id;
            if (isDispatcher) {
                btnAssign.setVisibility(View.VISIBLE);
            }
        });
        recyclerRankedWorkers.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerRankedWorkers.setAdapter(workerAdapter);
    }

    private void renderTask(Task task) {
        progressBar.setVisibility(View.GONE);
        tvTitle.setText(task.title);
        tvDescription.setText(task.description);
        chipStatus.setText(task.status);
        tvZone.setText(task.zoneId != null ? task.zoneId : "");
        tvTimeWindow.setText(
                TIME_FMT.format(new Date(task.windowStart))
                + " – " + TIME_FMT.format(new Date(task.windowEnd)));
        tvPriority.setText(task.priority);

        // Assigned worker section
        if (task.assignedWorkerId != null && !task.assignedWorkerId.isEmpty()) {
            cardAssignedWorker.setVisibility(View.VISIBLE);
            tvWorkerName.setText(task.assignedWorkerId); // fallback to ID
            // Resolve worker name and reputation in background
            final String wId = task.assignedWorkerId;
            final String wOrgId = orgId != null ? orgId : "";
            java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                com.roadrunner.dispatch.core.domain.model.Worker w =
                        ServiceLocator.getInstance().getWorkerRepository().getByIdScoped(wId, wOrgId);
                if (w != null && isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        tvWorkerName.setText(w.name);
                        chipReputation.setText(String.format(Locale.ROOT, "%.1f", w.reputationScore));
                        chipReputation.setVisibility(View.VISIBLE);
                    });
                }
            });
        } else {
            cardAssignedWorker.setVisibility(View.GONE);
        }

        updateActionButtons(task);
    }

    private void updateActionButtons(Task task) {
        // Hide all first
        btnClaim.setVisibility(View.GONE);
        btnStart.setVisibility(View.GONE);
        btnComplete.setVisibility(View.GONE);
        btnAssign.setVisibility(View.GONE);

        if (isDispatcher) {
            // Dispatcher: assign button shown after selecting worker
            if (selectedWorkerId != null) {
                btnAssign.setVisibility(View.VISIBLE);
            }
            btnAssign.setOnClickListener(v -> {
                if (selectedWorkerId != null) {
                    progressBar.setVisibility(View.VISIBLE);
                    tvError.setVisibility(View.GONE);
                    viewModel.acceptTask(taskId, selectedWorkerId, "DISPATCHER");
                }
            });
        } else {
            // Worker role
            if ("OPEN".equals(task.status) && "GRAB_ORDER".equals(task.mode)) {
                btnClaim.setVisibility(View.VISIBLE);
                btnClaim.setOnClickListener(v -> {
                    progressBar.setVisibility(View.VISIBLE);
                    tvError.setVisibility(View.GONE);
                    viewModel.acceptTask(taskId, workerId, "WORKER");
                });
            } else if ("ASSIGNED".equals(task.status)
                    && workerId.equals(task.assignedWorkerId)) {
                btnStart.setVisibility(View.VISIBLE);
                btnStart.setOnClickListener(v -> {
                    progressBar.setVisibility(View.VISIBLE);
                    tvError.setVisibility(View.GONE);
                    viewModel.startTask(taskId, workerId, RoleGuard.currentRole());
                });
            } else if ("IN_PROGRESS".equals(task.status)
                    && workerId.equals(task.assignedWorkerId)) {
                btnComplete.setVisibility(View.VISIBLE);
                btnComplete.setOnClickListener(v -> {
                    progressBar.setVisibility(View.VISIBLE);
                    tvError.setVisibility(View.GONE);
                    viewModel.completeTask(taskId, workerId, "WORKER");
                });
            }
        }
    }
}
