package com.roadrunner.dispatch.presentation.compliance.cases;

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
import com.roadrunner.dispatch.core.domain.model.ComplianceCase;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import java.util.List;

/**
 * Fragment listing compliance cases with tab-based status filtering.
 */
public class CaseListFragment extends Fragment implements CaseAdapter.OnCaseClickListener {

    private static final String ARG_ORG_ID      = "org_id";
    private static final String ARG_REVIEWER_ID = "reviewer_id";

    private ComplianceCaseViewModel viewModel;
    private CaseAdapter adapter;
    private androidx.lifecycle.LiveData<List<ComplianceCase>> currentCaseSource;

    private TabLayout tabStatus;
    private RecyclerView recyclerCases;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private FloatingActionButton fabOpenCase;

    private String orgId;
    private String reviewerId;

    private static final String[] TAB_STATUSES = {
            null, "OPEN", "UNDER_REVIEW", "RESOLVED"
    };

    public static CaseListFragment newInstance(String orgId, String reviewerId) {
        CaseListFragment f = new CaseListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORG_ID, orgId);
        args.putString(ARG_REVIEWER_ID, reviewerId);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            orgId      = getArguments().getString(ARG_ORG_ID, "");
            reviewerId = getArguments().getString(ARG_REVIEWER_ID, "");
        }
        if (orgId == null || orgId.isEmpty()) {
            String sessionOrgId = ServiceLocator.getInstance().getSessionManager().getOrgId();
            if (sessionOrgId != null) orgId = sessionOrgId;
        }
        if (reviewerId == null || reviewerId.isEmpty()) {
            String sessionUserId = ServiceLocator.getInstance().getSessionManager().getUserId();
            if (sessionUserId != null) reviewerId = sessionUserId;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_case_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!RoleGuard.hasRole("COMPLIANCE_REVIEWER")) {
            TextView tvDenied = new TextView(requireContext());
            tvDenied.setText("Access denied. Compliance Reviewer role required.");
            tvDenied.setPadding(32, 32, 32, 32);
            ((ViewGroup) view).addView(tvDenied);
            return;
        }

        tabStatus     = view.findViewById(R.id.tab_status);
        recyclerCases = view.findViewById(R.id.recycler_cases);
        progressBar   = view.findViewById(R.id.progress_bar);
        tvEmpty       = view.findViewById(R.id.tv_empty);
        fabOpenCase   = view.findViewById(R.id.fab_open_case);

        // Add tabs
        tabStatus.addTab(tabStatus.newTab().setText(R.string.tab_all));
        tabStatus.addTab(tabStatus.newTab().setText(R.string.tab_open));
        tabStatus.addTab(tabStatus.newTab().setText(R.string.tab_under_review));
        tabStatus.addTab(tabStatus.newTab().setText(R.string.tab_resolved));

        adapter = new CaseAdapter(this);
        recyclerCases.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerCases.setAdapter(adapter);

        viewModel = new ViewModelProvider(this,
                new ComplianceCaseViewModelFactory(ServiceLocator.getInstance())
        ).get(ComplianceCaseViewModel.class);

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText(error);
            }
        });

        loadForTab(0);

        tabStatus.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadForTab(tab.getPosition());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        fabOpenCase.setOnClickListener(v -> showOpenCaseDialog());
    }

    private void loadForTab(int position) {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        if (currentCaseSource != null) {
            currentCaseSource.removeObservers(getViewLifecycleOwner());
        }

        String status = TAB_STATUSES[position];
        if (status == null) {
            currentCaseSource = viewModel.getCases(orgId);
        } else {
            currentCaseSource = viewModel.getCasesByStatus(orgId, status);
        }
        currentCaseSource.observe(getViewLifecycleOwner(), this::renderList);
    }

    private void renderList(List<ComplianceCase> cases) {
        progressBar.setVisibility(View.GONE);
        if (cases == null || cases.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerCases.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerCases.setVisibility(View.VISIBLE);
            adapter.submitList(cases);
        }
    }

    @Override
    public void onCaseClick(ComplianceCase complianceCase) {
        Bundle bundle = new Bundle();
        bundle.putString("case_id", complianceCase.id);
        bundle.putString("org_id", orgId);
        bundle.putString("reviewer_id", reviewerId);
        Navigation.findNavController(requireView()).navigate(R.id.action_cases_to_detail, bundle);
    }

    private void showOpenCaseDialog() {
        OpenCaseDialogFragment dialog = OpenCaseDialogFragment.newInstance(orgId, reviewerId);
        dialog.show(getChildFragmentManager(), OpenCaseDialogFragment.TAG);
    }
}
