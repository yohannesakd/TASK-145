package com.roadrunner.dispatch.presentation.dispatch.tasklist;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.dispatch.zone.ZoneViewModel;
import com.roadrunner.dispatch.presentation.dispatch.zone.ZoneViewModelFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Dialog fragment for creating a new task.
 *
 * <p>Zones are loaded from {@link ZoneViewModel}.
 * On submit, calls {@link TaskListViewModel#createTask}.
 */
public class CreateTaskDialogFragment extends DialogFragment {

    public static final String TAG = "CreateTaskDialog";

    private static final String ARG_ORG_ID     = "org_id";
    private static final String ARG_CREATED_BY = "created_by";

    private ZoneViewModel  zoneViewModel;
    private TaskListViewModel taskListViewModel;

    private List<Zone> zoneList = new ArrayList<>();

    // Window timestamps (set by date/time picker)
    private long windowStart = System.currentTimeMillis();
    private long windowEnd   = System.currentTimeMillis() + 3_600_000L; // +1 hour

    public static CreateTaskDialogFragment newInstance(String orgId, String createdBy) {
        CreateTaskDialogFragment f = new CreateTaskDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORG_ID, orgId);
        args.putString(ARG_CREATED_BY, createdBy);
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_task, null);

        if (getArguments() == null
                || getArguments().getString(ARG_ORG_ID, "").isEmpty()
                || getArguments().getString(ARG_CREATED_BY, "").isEmpty()) {
            dismiss();
            return new AlertDialog.Builder(requireContext())
                    .setTitle("Error")
                    .setMessage("Missing required context (org_id, created_by). Cannot create task.")
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
        }
        String orgId     = getArguments().getString(ARG_ORG_ID, "");
        String createdBy = getArguments().getString(ARG_CREATED_BY, "");

        // ViewModels
        ServiceLocator sl = ServiceLocator.getInstance();
        zoneViewModel = new ViewModelProvider(this,
                new ZoneViewModelFactory(sl)).get(ZoneViewModel.class);
        taskListViewModel = new ViewModelProvider(requireParentFragment(),
                new TaskListViewModelFactory(sl)).get(TaskListViewModel.class);

        // Bind views
        TextInputEditText etTitle       = view.findViewById(R.id.et_title);
        TextInputEditText etDescription = view.findViewById(R.id.et_description);
        Spinner           spinnerZone   = view.findViewById(R.id.spinner_zone);
        RadioGroup        rgMode        = view.findViewById(R.id.rg_mode);
        TextInputEditText etPriority    = view.findViewById(R.id.et_priority);
        TextInputEditText etWindowStart = view.findViewById(R.id.et_window_start);
        TextInputEditText etWindowEnd   = view.findViewById(R.id.et_window_end);
        TextView          tvError       = view.findViewById(R.id.tv_error);
        View              btnCreate     = view.findViewById(R.id.btn_create);

        // Populate zone spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, new ArrayList<>());
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerZone.setAdapter(spinnerAdapter);

        zoneViewModel.getZones(orgId).observe(this, zones -> {
            zoneList.clear();
            if (zones != null) zoneList.addAll(zones);
            spinnerAdapter.clear();
            for (Zone z : zoneList) spinnerAdapter.add(z.name);
            spinnerAdapter.notifyDataSetChanged();
        });

        // Time window pickers
        etWindowStart.setText(formatEpoch(windowStart));
        etWindowEnd.setText(formatEpoch(windowEnd));

        etWindowStart.setOnClickListener(v ->
                showDateTimePicker(ts -> {
                    windowStart = ts;
                    etWindowStart.setText(formatEpoch(ts));
                }));
        etWindowEnd.setOnClickListener(v ->
                showDateTimePicker(ts -> {
                    windowEnd = ts;
                    etWindowEnd.setText(formatEpoch(ts));
                }));

        // Create button
        btnCreate.setOnClickListener(v -> {
            String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
            String desc  = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
            String priorityStr = etPriority.getText() != null ? etPriority.getText().toString().trim() : "";

            if (TextUtils.isEmpty(title)) {
                tvError.setVisibility(View.VISIBLE);
                tvError.setText(getString(R.string.hint_task_title) + " is required");
                return;
            }
            if (zoneList.isEmpty()) {
                tvError.setVisibility(View.VISIBLE);
                tvError.setText("No zones available");
                return;
            }

            int priority = 5;
            try { priority = Integer.parseInt(priorityStr); } catch (NumberFormatException ignored) {}

            int selectedZonePos = spinnerZone.getSelectedItemPosition();
            String zoneId = (selectedZonePos >= 0 && selectedZonePos < zoneList.size())
                    ? zoneList.get(selectedZonePos).id : "";

            String mode = (rgMode.getCheckedRadioButtonId() == R.id.rb_assigned)
                    ? "ASSIGNED" : "GRAB_ORDER";

            String actorRole = sl.getSessionManager().getRole();

            // Scan content before creating
            final int finalPriority = priority;
            final String finalZoneId = zoneId;
            final String finalMode = mode;
            final String finalActorRole = actorRole != null ? actorRole : "";

            taskListViewModel.scanContent(title + " " + desc);
            taskListViewModel.getScanResult().observe(this, new androidx.lifecycle.Observer<com.roadrunner.dispatch.core.domain.model.ContentScanResult>() {
                @Override
                public void onChanged(com.roadrunner.dispatch.core.domain.model.ContentScanResult scanResult) {
                    taskListViewModel.getScanResult().removeObserver(this);
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
                                .setPositiveButton("Proceed", (dialog, which) -> {
                                    taskListViewModel.createTask(orgId, title, desc, finalMode,
                                            finalPriority, finalZoneId, windowStart, windowEnd,
                                            createdBy, finalActorRole, true);
                                    dismiss();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        return;
                    }

                    taskListViewModel.createTask(orgId, title, desc, finalMode, finalPriority,
                            finalZoneId, windowStart, windowEnd, createdBy, finalActorRole);
                    dismiss();
                }
            });
        });

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_create_task_title)
                .setView(view)
                .setNegativeButton(R.string.cancel, (d, w) -> dismiss())
                .create();
    }

    private String formatEpoch(long ms) {
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault());
        return fmt.format(new java.util.Date(ms));
    }

    /** Minimal date/time picker using Calendar. In production this would use MaterialDatePicker. */
    private void showDateTimePicker(OnTimestampPicked callback) {
        Calendar cal = Calendar.getInstance();
        android.app.DatePickerDialog dpd = new android.app.DatePickerDialog(
                requireContext(),
                (datePicker, year, month, dayOfMonth) -> {
                    cal.set(year, month, dayOfMonth);
                    new android.app.TimePickerDialog(requireContext(),
                            (tp, hourOfDay, minute) -> {
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                cal.set(Calendar.MINUTE, minute);
                                callback.onPicked(cal.getTimeInMillis());
                            },
                            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false
                    ).show();
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        );
        dpd.show();
    }

    private interface OnTimestampPicked {
        void onPicked(long timestamp);
    }
}
