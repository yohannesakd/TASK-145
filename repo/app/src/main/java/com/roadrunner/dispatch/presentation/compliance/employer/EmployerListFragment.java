package com.roadrunner.dispatch.presentation.compliance.employer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.LiveData;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import java.util.List;

/**
 * Fragment listing employers with chip-group filter and navigation to detail/onboarding.
 */
public class EmployerListFragment extends Fragment implements EmployerAdapter.OnEmployerClickListener {

    private static final String ARG_ORG_ID = "org_id";

    private EmployerViewModel viewModel;
    private EmployerAdapter adapter;

    private ChipGroup chipGroupFilter;
    private RecyclerView recyclerEmployers;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private FloatingActionButton fabAddEmployer;

    private String orgId;
    private String currentFilter = null; // null = all
    private LiveData<List<Employer>> currentEmployerSource;

    public static EmployerListFragment newInstance(String orgId) {
        EmployerListFragment f = new EmployerListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORG_ID, orgId);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        orgId = getArguments() != null ? getArguments().getString(ARG_ORG_ID, "") : "";
        if (orgId == null || orgId.isEmpty()) {
            String sessionOrgId = ServiceLocator.getInstance().getSessionManager().getOrgId();
            if (sessionOrgId != null) orgId = sessionOrgId;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_employer_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!RoleGuard.hasRole("COMPLIANCE_REVIEWER", "ADMIN")) {
            TextView tvDenied = new TextView(requireContext());
            tvDenied.setText("Access denied. Compliance Reviewer or Admin role required.");
            tvDenied.setPadding(32, 32, 32, 32);
            ((ViewGroup) view).addView(tvDenied);
            return;
        }

        chipGroupFilter   = view.findViewById(R.id.chip_group_filter);
        recyclerEmployers = view.findViewById(R.id.recycler_employers);
        progressBar       = view.findViewById(R.id.progress_bar);
        tvEmpty           = view.findViewById(R.id.tv_empty);
        fabAddEmployer    = view.findViewById(R.id.fab_add_employer);

        adapter = new EmployerAdapter(this);
        recyclerEmployers.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerEmployers.setAdapter(adapter);

        viewModel = new ViewModelProvider(this,
                new EmployerViewModelFactory(ServiceLocator.getInstance())
        ).get(EmployerViewModel.class);

        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if      (id == R.id.chip_filter_pending)   currentFilter = "PENDING";
            else if (id == R.id.chip_filter_verified)  currentFilter = "VERIFIED";
            else if (id == R.id.chip_filter_suspended) currentFilter = "SUSPENDED";
            else                                        currentFilter = null;
            loadEmployers();
        });

        fabAddEmployer.setOnClickListener(v -> openEmployerDetail(null));

        loadEmployers();
    }

    private void loadEmployers() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        if (currentEmployerSource != null) {
            currentEmployerSource.removeObservers(getViewLifecycleOwner());
        }

        if (currentFilter == null) {
            currentEmployerSource = viewModel.getEmployers(orgId);
        } else {
            currentEmployerSource = viewModel.getEmployersByStatus(orgId, currentFilter);
        }
        currentEmployerSource.observe(getViewLifecycleOwner(), this::renderList);
    }

    private void renderList(List<Employer> employers) {
        progressBar.setVisibility(View.GONE);
        if (employers == null || employers.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerEmployers.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerEmployers.setVisibility(View.VISIBLE);
            adapter.submitList(employers);
        }
    }

    @Override
    public void onEmployerClick(Employer employer) {
        openEmployerDetail(employer);
    }

    private void openEmployerDetail(Employer employer) {
        Bundle bundle = new Bundle();
        bundle.putString("employer_id", employer != null ? employer.id : null);
        bundle.putString("org_id", orgId);
        Navigation.findNavController(requireView()).navigate(R.id.action_employers_to_detail, bundle);
    }
}
