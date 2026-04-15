package com.roadrunner.dispatch.presentation.dispatch.zone;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import java.util.List;

/**
 * Fragment that lists zones and allows creating new ones via a FAB dialog.
 */
public class ZoneFragment extends Fragment {

    private static final String ARG_ORG_ID = "org_id";

    private ZoneViewModel viewModel;
    private ZoneAdapter adapter;

    private RecyclerView recyclerZones;
    private TextView tvEmpty;
    private FloatingActionButton fabAddZone;

    private String orgId;

    public static ZoneFragment newInstance(String orgId) {
        ZoneFragment f = new ZoneFragment();
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
        return inflater.inflate(R.layout.fragment_zones, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!RoleGuard.hasRole("DISPATCHER", "ADMIN")) {
            android.widget.TextView tvError = new android.widget.TextView(requireContext());
            tvError.setText("Access denied. Dispatcher or Admin role required.");
            tvError.setPadding(32, 32, 32, 32);
            ((ViewGroup) view).addView(tvError);
            return;
        }

        recyclerZones = view.findViewById(R.id.recycler_zones);
        tvEmpty       = view.findViewById(R.id.tv_empty);
        fabAddZone    = view.findViewById(R.id.fab_add_zone);

        adapter = new ZoneAdapter();
        recyclerZones.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerZones.setAdapter(adapter);

        viewModel = new ViewModelProvider(this,
                new ZoneViewModelFactory(ServiceLocator.getInstance())
        ).get(ZoneViewModel.class);

        viewModel.getZones(orgId).observe(getViewLifecycleOwner(), this::renderZones);

        fabAddZone.setOnClickListener(v -> showCreateZoneDialog());
    }

    private void renderZones(List<Zone> zones) {
        if (zones == null || zones.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerZones.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerZones.setVisibility(View.VISIBLE);
            adapter.submitList(zones);
        }
    }

    private void showCreateZoneDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_zone, null);

        TextInputEditText etName  = dialogView.findViewById(R.id.et_zone_name);
        TextInputEditText etScore = dialogView.findViewById(R.id.et_zone_score);
        TextInputEditText etDesc  = dialogView.findViewById(R.id.et_zone_description);
        TextView tvError          = dialogView.findViewById(R.id.tv_error);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_create_zone_title)
                .setView(dialogView)
                .setPositiveButton(R.string.create, (d, w) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    String scoreStr = etScore.getText() != null ? etScore.getText().toString().trim() : "";
                    String desc = etDesc.getText() != null ? etDesc.getText().toString().trim() : "";

                    if (TextUtils.isEmpty(name)) {
                        tvError.setText(R.string.hint_zone_name);
                        tvError.setVisibility(View.VISIBLE);
                        return;
                    }
                    int score = 3;
                    try { score = Integer.parseInt(scoreStr); } catch (NumberFormatException ignored) {}

                    viewModel.createZone(orgId, name, score, desc.isEmpty() ? null : desc);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
