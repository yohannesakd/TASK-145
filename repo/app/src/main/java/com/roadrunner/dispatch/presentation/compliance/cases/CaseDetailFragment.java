package com.roadrunner.dispatch.presentation.compliance.cases;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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
import com.roadrunner.dispatch.core.domain.model.ComplianceCase;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;
import com.roadrunner.dispatch.presentation.compliance.reports.ReportViewModel;
import com.roadrunner.dispatch.presentation.compliance.reports.ReportViewModelFactory;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;

/**
 * Detail screen for a compliance case.
 *
 * <p>Shows case info, linked employer, reports, audit log, and enforcement actions.
 * Action buttons are only visible for the COMPLIANCE_REVIEWER role.
 */
public class CaseDetailFragment extends Fragment {

    private static final String ARG_CASE_ID     = "case_id";
    private static final String ARG_ORG_ID      = "org_id";
    private static final String ARG_REVIEWER_ID = "reviewer_id";

    private ComplianceCaseViewModel caseViewModel;
    private ReportViewModel reportViewModel;

    // Case info
    private Chip chipCaseType;
    private Chip chipSeverity;
    private Chip chipStatus;
    private TextView tvDescription;
    private TextView tvCreatedBy;
    private TextView tvAssignedTo;

    // Linked employer
    private MaterialCardView cardLinkedEmployer;
    private TextView tvEmployerName;
    private TextView tvEmployerEin;

    // Reports
    private RecyclerView recyclerReports;
    private ReportCompactAdapter reportAdapter;

    // Audit log
    private RecyclerView recyclerAuditLog;
    private AuditLogAdapter auditLogAdapter;

    // Actions
    private MaterialCardView cardActions;
    private CheckBox cbZeroTolerance;
    private MaterialButton btnIssueWarning;
    private MaterialButton btnSuspend7;
    private MaterialButton btnSuspend30;
    private MaterialButton btnSuspend365;
    private MaterialButton btnThrottle;
    private MaterialButton btnTakedown;

    private TextView tvError;

    private String caseId;
    private String orgId;
    private String reviewerId;
    private ComplianceCase loadedCase;

    public static CaseDetailFragment newInstance(String caseId, String orgId, String reviewerId) {
        CaseDetailFragment f = new CaseDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CASE_ID, caseId);
        args.putString(ARG_ORG_ID, orgId);
        args.putString(ARG_REVIEWER_ID, reviewerId);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            caseId     = getArguments().getString(ARG_CASE_ID, "");
            orgId      = getArguments().getString(ARG_ORG_ID, "");
            reviewerId = getArguments().getString(ARG_REVIEWER_ID, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_case_detail, container, false);
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
        bindViews(view);
        setupRecyclers();

        ServiceLocator sl = ServiceLocator.getInstance();

        caseViewModel = new ViewModelProvider(this,
                new ComplianceCaseViewModelFactory(sl)).get(ComplianceCaseViewModel.class);
        reportViewModel = new ViewModelProvider(this,
                new ReportViewModelFactory(sl)).get(ReportViewModel.class);

        // Observe case list and find our case
        caseViewModel.getCases(orgId).observe(getViewLifecycleOwner(), cases -> {
            if (cases == null) return;
            for (ComplianceCase c : cases) {
                if (c.id.equals(caseId)) {
                    loadedCase = c;
                    renderCase(c);
                    break;
                }
            }
        });

        // Reports
        reportViewModel.getReportsForCase(caseId, orgId).observe(getViewLifecycleOwner(), reports ->
                reportAdapter.submitList(reports));

        // Audit log
        caseViewModel.getAuditLogsForCase(caseId, orgId).observe(getViewLifecycleOwner(), logs ->
                auditLogAdapter.submitList(logs));

        // Errors
        caseViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                tvError.setVisibility(View.VISIBLE);
                tvError.setText(error);
            }
        });

        // Show actions only if the session role is COMPLIANCE_REVIEWER
        if (RoleGuard.hasRole("COMPLIANCE_REVIEWER")) {
            cardActions.setVisibility(View.VISIBLE);
            setupActionButtons();
        }

        // "File Report" button — navigates to report screen with case_id linked
        MaterialButton btnFileReport = view.findViewById(R.id.btn_file_report);
        if (btnFileReport != null) {
            btnFileReport.setOnClickListener(v -> {
                Bundle reportArgs = new Bundle();
                reportArgs.putString("org_id", orgId);
                reportArgs.putString("reported_by", reviewerId);
                reportArgs.putString("case_id", caseId);
                NavController nav = Navigation.findNavController(v);
                nav.navigate(R.id.action_case_detail_to_report, reportArgs);
            });
        }
    }

    private void bindViews(View root) {
        chipCaseType   = root.findViewById(R.id.chip_case_type);
        chipSeverity   = root.findViewById(R.id.chip_severity);
        chipStatus     = root.findViewById(R.id.chip_status);
        tvDescription  = root.findViewById(R.id.tv_description);
        tvCreatedBy    = root.findViewById(R.id.tv_created_by);
        tvAssignedTo   = root.findViewById(R.id.tv_assigned_to);

        cardLinkedEmployer = root.findViewById(R.id.card_linked_employer);
        tvEmployerName     = root.findViewById(R.id.tv_employer_name);
        tvEmployerEin      = root.findViewById(R.id.tv_employer_ein);

        recyclerReports  = root.findViewById(R.id.recycler_reports);
        recyclerAuditLog = root.findViewById(R.id.recycler_audit_log);

        cardActions    = root.findViewById(R.id.card_actions);
        cbZeroTolerance = root.findViewById(R.id.cb_zero_tolerance);
        btnIssueWarning = root.findViewById(R.id.btn_issue_warning);
        btnSuspend7    = root.findViewById(R.id.btn_suspend_7);
        btnSuspend30   = root.findViewById(R.id.btn_suspend_30);
        btnSuspend365  = root.findViewById(R.id.btn_suspend_365);
        btnThrottle    = root.findViewById(R.id.btn_throttle);
        btnTakedown    = root.findViewById(R.id.btn_takedown);

        tvError = root.findViewById(R.id.tv_error);
    }

    private void setupRecyclers() {
        reportAdapter = new ReportCompactAdapter();
        recyclerReports.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerReports.setAdapter(reportAdapter);
        recyclerReports.setNestedScrollingEnabled(false);

        auditLogAdapter = new AuditLogAdapter();
        recyclerAuditLog.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerAuditLog.setAdapter(auditLogAdapter);
        recyclerAuditLog.setNestedScrollingEnabled(false);
    }

    private void renderCase(ComplianceCase c) {
        chipCaseType.setText(c.caseType);
        chipSeverity.setText(c.severity);
        chipStatus.setText(c.status);
        tvDescription.setText(c.description);
        tvCreatedBy.setText(getString(R.string.label_created_by) + ": " + c.createdBy);
        if (c.assignedTo != null && !c.assignedTo.isEmpty()) {
            tvAssignedTo.setText(getString(R.string.label_assigned_to) + ": " + c.assignedTo);
            tvAssignedTo.setVisibility(View.VISIBLE);
        }

        if (c.employerId != null && !c.employerId.isEmpty()) {
            cardLinkedEmployer.setVisibility(View.VISIBLE);
            tvEmployerName.setText(c.employerId); // fallback to ID
            // Resolve employer details in background
            java.util.concurrent.ExecutorService bgExec = java.util.concurrent.Executors.newSingleThreadExecutor();
            final String empId = c.employerId;
            final String eOrgId = orgId != null ? orgId : "";
            bgExec.execute(() -> {
                com.roadrunner.dispatch.core.domain.model.Employer employer =
                        ServiceLocator.getInstance().getEmployerRepository().getByIdScoped(empId, eOrgId);
                if (employer != null && isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        tvEmployerName.setText(employer.legalName);
                        tvEmployerEin.setText(employer.ein);
                        tvEmployerEin.setVisibility(View.VISIBLE);
                    });
                }
            });
        }
    }

    private void setupActionButtons() {
        btnIssueWarning.setOnClickListener(v -> enforce("WARN", currentCase()));
        btnSuspend7.setOnClickListener(v    -> enforce("SUSPEND_7", currentCase()));
        btnSuspend30.setOnClickListener(v   -> enforce("SUSPEND_30", currentCase()));
        btnSuspend365.setOnClickListener(v  -> enforce("SUSPEND_365", currentCase()));
        btnThrottle.setOnClickListener(v    -> enforce("THROTTLE", currentCase()));
        btnTakedown.setOnClickListener(v    -> enforce("TAKEDOWN", currentCase()));
    }

    private String currentCase() {
        return loadedCase != null && loadedCase.employerId != null ? loadedCase.employerId : "";
    }

    private void enforce(String action, String employerId) {
        if (employerId == null || employerId.isEmpty()) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("No employer linked to this case");
            return;
        }
        boolean isZeroTolerance = cbZeroTolerance.isChecked();
        tvError.setVisibility(View.GONE);
        String actorRole = ServiceLocator.getInstance().getSessionManager().getRole();
        caseViewModel.enforceViolation(employerId, action, reviewerId, caseId, orgId,
                isZeroTolerance, actorRole != null ? actorRole : "");
    }
}
