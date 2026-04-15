package com.roadrunner.dispatch.presentation.compliance;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;
import com.roadrunner.dispatch.presentation.compliance.cases.ComplianceCaseViewModel;
import com.roadrunner.dispatch.presentation.compliance.cases.ComplianceCaseViewModelFactory;
import com.roadrunner.dispatch.presentation.compliance.employer.EmployerViewModel;
import com.roadrunner.dispatch.presentation.compliance.employer.EmployerViewModelFactory;
import com.roadrunner.dispatch.presentation.compliance.reports.ReportViewModel;
import com.roadrunner.dispatch.presentation.compliance.reports.ReportViewModelFactory;

/**
 * Compliance dashboard with summary cards for Open Cases, Pending Employers, and Reports.
 * Each card shows a count and navigates to the corresponding list.
 */
public class ComplianceDashboardFragment extends Fragment {

    private static final String ARG_ORG_ID      = "org_id";
    private static final String ARG_REVIEWER_ID = "reviewer_id";

    private ComplianceCaseViewModel caseViewModel;
    private EmployerViewModel employerViewModel;
    private ReportViewModel reportViewModel;

    public ComplianceDashboardFragment() {}

    public static ComplianceDashboardFragment newInstance(String orgId, String reviewerId) {
        ComplianceDashboardFragment f = new ComplianceDashboardFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORG_ID, orgId);
        args.putString(ARG_REVIEWER_ID, reviewerId);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_compliance_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!RoleGuard.hasRole("COMPLIANCE_REVIEWER", "ADMIN")) {
            android.widget.TextView tvError = new android.widget.TextView(requireContext());
            tvError.setText("Access denied. Compliance Reviewer or Admin role required.");
            tvError.setPadding(32, 32, 32, 32);
            ((ViewGroup) view).addView(tvError);
            return;
        }

        String orgId      = getArguments() != null ? getArguments().getString(ARG_ORG_ID, "") : "";
        String reviewerId = getArguments() != null ? getArguments().getString(ARG_REVIEWER_ID, "") : "";

        ServiceLocator sl = ServiceLocator.getInstance();

        caseViewModel    = new ViewModelProvider(this, new ComplianceCaseViewModelFactory(sl))
                .get(ComplianceCaseViewModel.class);
        employerViewModel = new ViewModelProvider(this, new EmployerViewModelFactory(sl))
                .get(EmployerViewModel.class);
        reportViewModel  = new ViewModelProvider(this, new ReportViewModelFactory(sl))
                .get(ReportViewModel.class);

        TextView tvOpenCases         = view.findViewById(R.id.tv_open_cases_count);
        TextView tvPendingEmployers  = view.findViewById(R.id.tv_pending_employers_count);
        TextView tvReportsCount      = view.findViewById(R.id.tv_reports_count);

        // Live counts
        caseViewModel.getCasesByStatus(orgId, "OPEN").observe(getViewLifecycleOwner(), cases ->
                tvOpenCases.setText(cases != null ? String.valueOf(cases.size()) : "0"));

        employerViewModel.getEmployersByStatus(orgId, "PENDING")
                .observe(getViewLifecycleOwner(), employers ->
                        tvPendingEmployers.setText(employers != null
                                ? String.valueOf(employers.size()) : "0"));

        reportViewModel.getReports(orgId).observe(getViewLifecycleOwner(), reports ->
                tvReportsCount.setText(reports != null ? String.valueOf(reports.size()) : "0"));

        // Navigation via NavController
        NavController nav = Navigation.findNavController(view);

        // Open Cases card: only COMPLIANCE_REVIEWER can access cases (not ADMIN)
        View cardOpenCases = view.findViewById(R.id.card_open_cases);
        if (RoleGuard.hasRole("COMPLIANCE_REVIEWER")) {
            cardOpenCases.setOnClickListener(v -> {
                Bundle casesArgs = new Bundle();
                casesArgs.putString("org_id", orgId);
                casesArgs.putString("reviewer_id", reviewerId);
                nav.navigate(R.id.action_compliance_to_cases, casesArgs);
            });
        } else {
            cardOpenCases.setAlpha(0.4f);
            cardOpenCases.setClickable(false);
        }

        view.findViewById(R.id.card_pending_employers).setOnClickListener(v -> {
            Bundle employersArgs = new Bundle();
            employersArgs.putString("org_id", orgId);
            employersArgs.putString("reviewer_id", reviewerId);
            nav.navigate(R.id.action_compliance_to_employers, employersArgs);
        });

        view.findViewById(R.id.card_reports).setOnClickListener(v -> {
            Bundle reportsArgs = new Bundle();
            reportsArgs.putString("org_id", orgId);
            reportsArgs.putString("reported_by", reviewerId);
            nav.navigate(R.id.action_compliance_to_reports, reportsArgs);
        });

        View cardTasks = view.findViewById(R.id.card_audit_tasks);
        if (cardTasks != null) {
            cardTasks.setOnClickListener(v -> {
                Bundle tasksArgs = new Bundle();
                tasksArgs.putString("org_id", orgId);
                nav.navigate(R.id.action_compliance_to_tasks, tasksArgs);
            });
        }

        View cardOrders = view.findViewById(R.id.card_audit_orders);
        if (cardOrders != null) {
            cardOrders.setOnClickListener(v -> {
                Bundle ordersArgs = new Bundle();
                ordersArgs.putString("orgId", orgId);
                nav.navigate(R.id.action_compliance_to_orders, ordersArgs);
            });
        }
    }
}
