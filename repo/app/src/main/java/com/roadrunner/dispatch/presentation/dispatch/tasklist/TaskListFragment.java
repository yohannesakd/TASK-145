package com.roadrunner.dispatch.presentation.dispatch.tasklist;

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
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.roadrunner.dispatch.R;
import androidx.lifecycle.LiveData;
import com.roadrunner.dispatch.core.domain.model.Task;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;
import com.roadrunner.dispatch.presentation.dispatch.taskdetail.TaskDetailFragment;

import java.util.List;

/**
 * Fragment that displays the task list for both dispatchers and workers.
 *
 * <p>Dispatchers see all tasks with a FAB to create new ones.
 * Workers see ranked/open tasks only (no FAB).
 * Tab filtering drives the status filter applied to the ViewModel query.
 */
public class TaskListFragment extends Fragment implements TaskAdapter.OnTaskClickListener {

    private static final String ARG_ORG_ID        = "org_id";
    private static final String ARG_IS_DISPATCHER = "is_dispatcher";
    private static final String ARG_WORKER_ID     = "worker_id";
    private static final String ARG_USE_RANKING   = "use_ranking";
    private static final String ARG_MY_TASKS      = "my_tasks";

    private TaskListViewModel viewModel;
    private TaskAdapter adapter;

    private TabLayout tabStatus;
    private RecyclerView recyclerTasks;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private FloatingActionButton fabCreateTask;

    private String orgId;
    private boolean isDispatcher;
    private String workerId;
    private boolean useRanking;
    private boolean myTasks;
    private LiveData<List<Task>> currentTaskSource;

    // Tab positions mapped to status values
    private static final String[] TAB_STATUSES = {
            null,          // 0 = All
            "OPEN",        // 1
            "ASSIGNED",    // 2
            "IN_PROGRESS", // 3
            "COMPLETED"    // 4
    };

    public static TaskListFragment newInstance(String orgId, boolean isDispatcher) {
        return newInstance(orgId, isDispatcher, "");
    }

    public static TaskListFragment newInstance(String orgId, boolean isDispatcher, String workerId) {
        TaskListFragment f = new TaskListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORG_ID, orgId);
        args.putBoolean(ARG_IS_DISPATCHER, isDispatcher);
        args.putString(ARG_WORKER_ID, workerId != null ? workerId : "");
        // For workers with a non-empty workerId, enable ranked task loading
        args.putBoolean(ARG_USE_RANKING, !isDispatcher && workerId != null && !workerId.isEmpty());
        args.putBoolean(ARG_MY_TASKS, false);
        f.setArguments(args);
        return f;
    }

    public static TaskListFragment newMyTasksInstance(String orgId, String workerId) {
        TaskListFragment f = new TaskListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORG_ID, orgId);
        args.putBoolean(ARG_IS_DISPATCHER, false);
        args.putString(ARG_WORKER_ID, workerId != null ? workerId : "");
        args.putBoolean(ARG_USE_RANKING, false);
        args.putBoolean(ARG_MY_TASKS, true);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            orgId        = getArguments().getString(ARG_ORG_ID, "");
            isDispatcher = getArguments().getBoolean(ARG_IS_DISPATCHER, false);
            workerId     = getArguments().getString(ARG_WORKER_ID, "");
            useRanking   = getArguments().getBoolean(ARG_USE_RANKING, false);
            myTasks      = getArguments().getBoolean(ARG_MY_TASKS, false);
        }
        if (orgId == null || orgId.isEmpty()) {
            String sessionOrgId = ServiceLocator.getInstance().getSessionManager().getOrgId();
            if (sessionOrgId != null) orgId = sessionOrgId;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_task_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabStatus    = view.findViewById(R.id.tab_status);
        recyclerTasks = view.findViewById(R.id.recycler_tasks);
        progressBar  = view.findViewById(R.id.progress_bar);
        tvEmpty      = view.findViewById(R.id.tv_empty);
        fabCreateTask = view.findViewById(R.id.fab_create_task);

        isDispatcher = RoleGuard.hasRole("DISPATCHER", "ADMIN");
        // COMPLIANCE_REVIEWER gets read-only org-wide task visibility (no FAB).
        boolean canViewOrgTasks = isDispatcher || RoleGuard.hasRole("COMPLIANCE_REVIEWER");

        // Set up tabs
        String[] tabLabels = {
                getString(R.string.tab_all),
                getString(R.string.tab_open),
                getString(R.string.tab_assigned),
                getString(R.string.tab_in_progress),
                getString(R.string.tab_completed)
        };
        for (String label : tabLabels) {
            tabStatus.addTab(tabStatus.newTab().setText(label));
        }

        // Set up RecyclerView
        adapter = new TaskAdapter(this);
        recyclerTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerTasks.setAdapter(adapter);

        // FAB visibility
        if (isDispatcher) {
            fabCreateTask.setVisibility(View.VISIBLE);
            fabCreateTask.setOnClickListener(v -> openCreateTaskDialog());
        }

        // ViewModel
        viewModel = new ViewModelProvider(this,
                new TaskListViewModelFactory(ServiceLocator.getInstance())
        ).get(TaskListViewModel.class);

        // Observe errors
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                tvEmpty.setText(error);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });

        // My Tasks mode: load worker's assigned/in-progress tasks
        if (myTasks && !workerId.isEmpty()) {
            viewModel.getMyTasks().observe(getViewLifecycleOwner(), tasks -> {
                progressBar.setVisibility(View.GONE);
                if (tasks == null || tasks.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    recyclerTasks.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    recyclerTasks.setVisibility(View.VISIBLE);
                    adapter.submitList(tasks);
                }
            });
            progressBar.setVisibility(View.VISIBLE);
            viewModel.loadMyTasks(workerId, orgId);
        } else
        // Fix 5: For workers with ranking enabled, load ranked grab-order tasks
        if (useRanking && !workerId.isEmpty()) {
            viewModel.getRankedTasks().observe(getViewLifecycleOwner(), scoredTasks -> {
                progressBar.setVisibility(View.GONE);
                if (scoredTasks == null || scoredTasks.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    recyclerTasks.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    recyclerTasks.setVisibility(View.VISIBLE);
                    // Map ScoredTask → Task for the adapter
                    java.util.List<Task> tasks = new java.util.ArrayList<>(scoredTasks.size());
                    for (com.roadrunner.dispatch.core.domain.usecase.MatchTasksUseCase.ScoredTask st : scoredTasks) {
                        tasks.add(st.task);
                    }
                    adapter.submitList(tasks);
                }
            });
            progressBar.setVisibility(View.VISIBLE);
            viewModel.loadRankedTasksByWorkerId(workerId, orgId, null);
        } else {
            if (!canViewOrgTasks) {
                // Workers should only see their own tasks — never the full org list.
                if (workerId != null && !workerId.isEmpty()) {
                    viewModel.loadMyTasks(workerId, orgId);
                    viewModel.getMyTasks().observe(getViewLifecycleOwner(), tasks -> {
                        if (tasks != null) {
                            progressBar.setVisibility(View.GONE);
                            if (tasks.isEmpty()) {
                                tvEmpty.setVisibility(View.VISIBLE);
                                recyclerTasks.setVisibility(View.GONE);
                            } else {
                                tvEmpty.setVisibility(View.GONE);
                                recyclerTasks.setVisibility(View.VISIBLE);
                                adapter.submitList(tasks);
                            }
                        }
                    });
                    progressBar.setVisibility(View.VISIBLE);
                }
                return;
            }
            // Dispatcher / Admin / COMPLIANCE_REVIEWER: load full org task list
            loadForTab(0);
        }

        if (!useRanking || workerId.isEmpty()) {
            tabStatus.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    loadForTab(tab.getPosition());
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });
        }
    }

    private void loadForTab(int position) {
        String status = TAB_STATUSES[position];
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        if (currentTaskSource != null) {
            currentTaskSource.removeObservers(getViewLifecycleOwner());
        }

        if (status == null) {
            currentTaskSource = viewModel.getTasks(orgId);
        } else {
            currentTaskSource = viewModel.getTasksByStatus(orgId, status);
        }
        currentTaskSource.observe(getViewLifecycleOwner(), this::updateList);
    }

    private void updateList(List<Task> tasks) {
        progressBar.setVisibility(View.GONE);
        if (tasks == null || tasks.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerTasks.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerTasks.setVisibility(View.VISIBLE);
            adapter.submitList(tasks);
        }
    }

    private void openCreateTaskDialog() {
        String userId = ServiceLocator.getInstance().getSessionManager().getUserId();
        CreateTaskDialogFragment dialog = CreateTaskDialogFragment.newInstance(orgId,
                userId != null ? userId : "");
        dialog.show(getChildFragmentManager(), CreateTaskDialogFragment.TAG);
    }

    @Override
    public void onTaskClick(Task task) {
        Bundle args = new Bundle();
        args.putString(TaskDetailFragment.ARG_TASK_ID, task.id);
        args.putBoolean(TaskDetailFragment.ARG_IS_DISPATCHER, isDispatcher);
        args.putString(TaskDetailFragment.ARG_ORG_ID, orgId);
        args.putString(TaskDetailFragment.ARG_WORKER_ID, workerId);
        Navigation.findNavController(requireView()).navigate(R.id.action_tasks_to_detail, args);
    }
}
