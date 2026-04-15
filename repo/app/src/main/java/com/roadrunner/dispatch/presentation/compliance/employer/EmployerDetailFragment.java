package com.roadrunner.dispatch.presentation.compliance.employer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Fragment for creating a new employer or viewing/editing an existing one.
 *
 * <p>When {@code employerId} is null, the form is empty (new employer flow).
 * When an existing ID is provided, fields are pre-populated and status info is shown.
 */
public class EmployerDetailFragment extends Fragment {

    private static final String ARG_ORG_ID      = "org_id";
    private static final String ARG_EMPLOYER_ID = "employer_id";

    private EmployerViewModel viewModel;

    // Form views
    private TextInputEditText etLegalName;
    private TextInputEditText etEin;
    private TextInputEditText etStreet;
    private TextInputEditText etCity;
    private TextInputEditText etState;
    private TextInputEditText etZip;

    // Status info (existing employer)
    private MaterialCardView cardStatusInfo;
    private Chip chipEmployerStatus;
    private TextView tvWarningCount;
    private TextView tvSuspendedUntil;
    private TextView tvThrottledStatus;

    private TextView tvError;
    private MaterialButton btnVerifySave;

    private String orgId;
    private String employerId;

    // Enforcement fields cached from the loaded employer; used in doSave() to avoid main-thread DB I/O.
    private int existingWarningCount = 0;
    private long existingSuspendedUntil = 0L;
    private boolean existingThrottled = false;
    private String existingStatus = "PENDING";

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public static EmployerDetailFragment newInstance(String orgId, @Nullable String employerId) {
        EmployerDetailFragment f = new EmployerDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORG_ID, orgId);
        args.putString(ARG_EMPLOYER_ID, employerId);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            orgId      = getArguments().getString(ARG_ORG_ID, "");
            employerId = getArguments().getString(ARG_EMPLOYER_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_employer_detail, container, false);
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

        viewModel = new ViewModelProvider(this,
                new EmployerViewModelFactory(ServiceLocator.getInstance())
        ).get(EmployerViewModel.class);

        viewModel.getSavedEmployer().observe(getViewLifecycleOwner(), employer -> {
            if (employer != null) {
                requireActivity().onBackPressed();
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                tvError.setVisibility(View.VISIBLE);
                tvError.setText(error);
            }
        });

        // Load existing employer if editing
        if (employerId != null) {
            viewModel.getEmployers(orgId).observe(getViewLifecycleOwner(), employers -> {
                if (employers == null) return;
                for (Employer e : employers) {
                    if (e.id.equals(employerId)) {
                        populateForm(e);
                        break;
                    }
                }
            });
        }

        btnVerifySave.setOnClickListener(v -> onVerifySave());
    }

    private void bindViews(View root) {
        etLegalName = root.findViewById(R.id.et_legal_name);
        etEin       = root.findViewById(R.id.et_ein);
        etStreet    = root.findViewById(R.id.et_street);
        etCity      = root.findViewById(R.id.et_city);
        etState     = root.findViewById(R.id.et_state);
        etZip       = root.findViewById(R.id.et_zip);

        cardStatusInfo      = root.findViewById(R.id.card_status_info);
        chipEmployerStatus  = root.findViewById(R.id.chip_employer_status);
        tvWarningCount      = root.findViewById(R.id.tv_warning_count);
        tvSuspendedUntil    = root.findViewById(R.id.tv_suspended_until);
        tvThrottledStatus   = root.findViewById(R.id.tv_throttled_status);

        tvError      = root.findViewById(R.id.tv_error);
        btnVerifySave = root.findViewById(R.id.btn_verify_save);
    }

    private void populateForm(Employer e) {
        // Cache enforcement state so doSave() can use it without a synchronous DB lookup.
        existingWarningCount   = e.warningCount;
        existingSuspendedUntil = e.suspendedUntil;
        existingThrottled      = e.throttled;
        existingStatus         = e.status;

        etLegalName.setText(e.legalName);
        etEin.setText(e.ein);
        etStreet.setText(e.streetAddress);
        etCity.setText(e.city);
        etState.setText(e.state);
        etZip.setText(e.zipCode);

        // Show status card
        cardStatusInfo.setVisibility(View.VISIBLE);
        chipEmployerStatus.setText(e.status);

        if (e.warningCount > 0) {
            tvWarningCount.setVisibility(View.VISIBLE);
            tvWarningCount.setText(getString(R.string.label_warning_count, e.warningCount));
        }
        if (e.suspendedUntil > 0) {
            tvSuspendedUntil.setVisibility(View.VISIBLE);
            tvSuspendedUntil.setText(getString(R.string.label_suspended_until,
                    DATE_FMT.format(new Date(e.suspendedUntil))));
        }
        tvThrottledStatus.setVisibility(e.throttled ? View.VISIBLE : View.GONE);
    }

    private void onVerifySave() {
        tvError.setVisibility(View.GONE);
        String legalName = text(etLegalName);
        String ein       = text(etEin);
        String street    = text(etStreet);
        String city      = text(etCity);
        String state     = text(etState);
        String zip       = text(etZip);

        if (legalName.isEmpty()) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText(getString(R.string.hint_legal_name) + " is required");
            return;
        }

        viewModel.scanContent(legalName + " " + ein + " " + street + " " + city);

        viewModel.getScanResult().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<com.roadrunner.dispatch.core.domain.model.ContentScanResult>() {
            @Override
            public void onChanged(com.roadrunner.dispatch.core.domain.model.ContentScanResult scanResult) {
                viewModel.getScanResult().removeObserver(this);
                if (scanResult == null) return;

                if ("ZERO_TOLERANCE".equals(scanResult.status)) {
                    tvError.setVisibility(View.VISIBLE);
                    tvError.setText("Content contains prohibited terms");
                    return;
                }

                if ("FLAGGED".equals(scanResult.status)) {
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Content Warning")
                            .setMessage("The content contains flagged terms. Do you want to proceed?")
                            .setPositiveButton("Proceed", (dialog, which) -> doSave(legalName, ein, street, city, state, zip))
                            .setNegativeButton("Cancel", null)
                            .show();
                    return;
                }

                doSave(legalName, ein, street, city, state, zip);
            }
        });
    }

    private void doSave(String legalName, String ein, String street, String city, String state, String zip) {
        // For existing employers, use the enforcement state cached in populateForm() so we avoid
        // a synchronous (main-thread) DB lookup here.
        String status       = employerId != null ? existingStatus         : "PENDING";
        int warningCount    = employerId != null ? existingWarningCount   : 0;
        long suspendedUntil = employerId != null ? existingSuspendedUntil : 0L;
        boolean throttled   = employerId != null && existingThrottled;

        Employer employer = new Employer(
                employerId,
                orgId,
                legalName,
                ein,
                street,
                city,
                state,
                zip,
                status,
                warningCount, suspendedUntil, throttled
        );
        String actorRole = ServiceLocator.getInstance().getSessionManager().getRole();
        viewModel.verifyEmployer(employer, actorRole != null ? actorRole : "");
    }

    private static String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
