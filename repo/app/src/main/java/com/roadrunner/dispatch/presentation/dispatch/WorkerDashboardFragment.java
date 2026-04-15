package com.roadrunner.dispatch.presentation.dispatch;

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
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;
import com.roadrunner.dispatch.presentation.dispatch.tasklist.TaskListFragment;
import com.roadrunner.dispatch.presentation.dispatch.tasklist.TaskListViewModel;
import com.roadrunner.dispatch.presentation.dispatch.tasklist.TaskListViewModelFactory;

import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Worker dashboard with reputation score display and task navigation cards.
 */
public class WorkerDashboardFragment extends Fragment {

    private static final String ARG_ORG_ID    = "org_id";
    private static final String ARG_WORKER_ID = "worker_id";
    private static final String ARG_USER_ID   = "user_id";

    private TaskListViewModel viewModel;

    public WorkerDashboardFragment() {}

    public static WorkerDashboardFragment newInstance(String orgId, String workerId, String userId) {
        WorkerDashboardFragment f = new WorkerDashboardFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORG_ID, orgId);
        args.putString(ARG_WORKER_ID, workerId);
        args.putString(ARG_USER_ID, userId);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_worker_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!RoleGuard.hasRole("WORKER")) {
            android.widget.TextView tvError = new android.widget.TextView(requireContext());
            tvError.setText("Access denied. Worker role required.");
            tvError.setPadding(32, 32, 32, 32);
            ((ViewGroup) view).addView(tvError);
            return;
        }

        String orgId    = getArguments() != null ? getArguments().getString(ARG_ORG_ID, "") : "";
        String workerIdArg = getArguments() != null ? getArguments().getString(ARG_WORKER_ID, "") : "";
        String userId   = getArguments() != null ? getArguments().getString(ARG_USER_ID, "") : "";

        // If workerId not supplied, resolve from userId via repository on a background thread
        // Use a single-element array so the lambda can update it
        final String[] resolvedWorkerId = { workerIdArg };

        viewModel = new ViewModelProvider(this,
                new TaskListViewModelFactory(ServiceLocator.getInstance())
        ).get(TaskListViewModel.class);

        // Disable navigation cards until workerId is resolved
        view.findViewById(R.id.card_available_tasks).setEnabled(false);
        view.findViewById(R.id.card_my_tasks).setEnabled(false);

        // Resolve workerId in background, then load counts and set up navigation
        Executors.newSingleThreadExecutor().execute(() -> {
            if (resolvedWorkerId[0].isEmpty() && !userId.isEmpty()) {
                Worker worker = ServiceLocator.getInstance().getWorkerRepository().getByUserIdScoped(userId, orgId);
                if (worker != null) {
                    resolvedWorkerId[0] = worker.id;
                }
            }

            if (view != null && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // Use resolved workerId directly to avoid redundant resolution
                    if (!resolvedWorkerId[0].isEmpty()) {
                        viewModel.loadRankedTasksByWorkerId(resolvedWorkerId[0], orgId, null);
                    } else {
                        // Fallback: let the ViewModel resolve from userId
                        viewModel.loadRankedTasksForWorker(userId, orgId, null);
                    }
                    viewModel.getRankedTasks().observe(getViewLifecycleOwner(), tasks -> {
                        if (tasks != null) {
                            TextView tvCount = view.findViewById(R.id.tv_available_tasks_count);
                            tvCount.setText(String.valueOf(tasks.size()));
                        }
                    });

                    // Load "My Tasks" count
                    if (!resolvedWorkerId[0].isEmpty()) {
                        viewModel.loadMyTasks(resolvedWorkerId[0], orgId);
                        viewModel.getMyTasks().observe(getViewLifecycleOwner(), myTasks -> {
                            if (myTasks != null) {
                                TextView tvMyCount = view.findViewById(R.id.tv_my_tasks_count);
                                tvMyCount.setText(String.valueOf(myTasks.size()));
                            }
                        });
                    }

                    // Enable navigation only after workerId is resolved
                    view.findViewById(R.id.card_available_tasks).setEnabled(true);
                    view.findViewById(R.id.card_my_tasks).setEnabled(true);

                    NavController nav = Navigation.findNavController(view);

                    // Navigation — "Available Tasks" goes to ranked open tasks
                    view.findViewById(R.id.card_available_tasks).setOnClickListener(v -> {
                        Bundle availArgs = new Bundle();
                        availArgs.putString("org_id", orgId);
                        availArgs.putString("worker_id", resolvedWorkerId[0]);
                        availArgs.putBoolean("use_ranking", true);
                        availArgs.putBoolean("my_tasks", false);
                        availArgs.putBoolean("is_dispatcher", false);
                        nav.navigate(R.id.action_worker_to_tasks, availArgs);
                    });

                    // "My Tasks" goes to worker's assigned/in-progress tasks
                    view.findViewById(R.id.card_my_tasks).setOnClickListener(v -> {
                        Bundle myArgs = new Bundle();
                        myArgs.putString("org_id", orgId);
                        myArgs.putString("worker_id", resolvedWorkerId[0]);
                        myArgs.putBoolean("use_ranking", false);
                        myArgs.putBoolean("my_tasks", true);
                        myArgs.putBoolean("is_dispatcher", false);
                        nav.navigate(R.id.action_worker_to_tasks, myArgs);
                    });

                    // Catalog browsing
                    View catalogCard = view.findViewById(R.id.card_catalog);
                    if (catalogCard != null) {
                        catalogCard.setEnabled(true);
                        catalogCard.setOnClickListener(v -> {
                            Bundle catalogArgs = new Bundle();
                            catalogArgs.putString("org_id", orgId);
                            catalogArgs.putString("worker_id", resolvedWorkerId[0]);
                            nav.navigate(R.id.action_worker_to_catalog, catalogArgs);
                        });
                    }

                    // Report filing
                    View reportsCard = view.findViewById(R.id.card_reports);
                    if (reportsCard != null) {
                        reportsCard.setEnabled(true);
                        reportsCard.setOnClickListener(v -> {
                            Bundle reportsArgs = new Bundle();
                            reportsArgs.putString("org_id", orgId);
                            reportsArgs.putString("reported_by", userId);
                            nav.navigate(R.id.action_worker_to_reports, reportsArgs);
                        });
                    }
                });
            }
        });
    }
}
