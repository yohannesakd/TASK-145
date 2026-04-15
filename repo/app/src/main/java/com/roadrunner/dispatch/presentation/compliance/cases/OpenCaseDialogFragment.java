package com.roadrunner.dispatch.presentation.compliance.cases;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.di.ServiceLocator;

/**
 * Dialog fragment for opening a new compliance case.
 */
public class OpenCaseDialogFragment extends DialogFragment {

    public static final String TAG = "OpenCaseDialog";

    private static final String ARG_ORG_ID      = "org_id";
    private static final String ARG_REVIEWER_ID = "reviewer_id";

    private static final String[] CASE_TYPES = {
            "CONTENT_VIOLATION", "WAGE_THEFT", "SAFETY_VIOLATION",
            "FRAUD", "DISCRIMINATION", "OTHER"
    };

    private static final String[] SEVERITIES = { "LOW", "MEDIUM", "HIGH", "CRITICAL" };

    public static OpenCaseDialogFragment newInstance(String orgId, String reviewerId) {
        OpenCaseDialogFragment f = new OpenCaseDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORG_ID, orgId);
        args.putString(ARG_REVIEWER_ID, reviewerId);
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_open_case, null);

        String orgId      = getArguments() != null ? getArguments().getString(ARG_ORG_ID, "") : "";
        String reviewerId = getArguments() != null ? getArguments().getString(ARG_REVIEWER_ID, "") : "";

        ComplianceCaseViewModel viewModel = new ViewModelProvider(requireParentFragment(),
                new ComplianceCaseViewModelFactory(ServiceLocator.getInstance())
        ).get(ComplianceCaseViewModel.class);

        Spinner spinnerType = view.findViewById(R.id.spinner_case_type);
        Spinner spinnerSev  = view.findViewById(R.id.spinner_severity);
        TextInputEditText etEmployerId  = view.findViewById(R.id.et_employer_id);
        TextInputEditText etDescription = view.findViewById(R.id.et_description);
        TextView tvError = view.findViewById(R.id.tv_error);

        spinnerType.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, CASE_TYPES));
        spinnerSev.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, SEVERITIES));

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.fab_open_case)
                .setView(view)
                .setPositiveButton(R.string.create, (d, w) -> {
                    String desc = etDescription.getText() != null
                            ? etDescription.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(desc)) {
                        tvError.setVisibility(View.VISIBLE);
                        tvError.setText(getString(R.string.hint_description) + " is required");
                        return;
                    }
                    String caseType   = CASE_TYPES[spinnerType.getSelectedItemPosition()];
                    String severity   = SEVERITIES[spinnerSev.getSelectedItemPosition()];
                    String employerId = etEmployerId.getText() != null
                            ? etEmployerId.getText().toString().trim() : null;

                    String actorRole = ServiceLocator.getInstance()
                            .getSessionManager().getRole();
                    if (actorRole == null || actorRole.isEmpty()) {
                        tvError.setVisibility(View.VISIBLE);
                        tvError.setText("Session expired. Please log in again.");
                        return;
                    }
                    viewModel.openCase(orgId, employerId, caseType, severity, desc,
                            reviewerId, actorRole);
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }
}
