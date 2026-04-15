package com.roadrunner.dispatch.presentation.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.core.domain.model.User;
import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import java.util.List;

/**
 * Admin screen for viewing and creating user accounts.
 *
 * <p>Lists all users for the current organisation and exposes a FAB-driven
 * dialog to create a new user via {@link com.roadrunner.dispatch.core.domain.usecase.RegisterUserUseCase}.
 */
public class UserManagementFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private UserAdapter adapter;
    private UserManagementViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_users);
        tvEmpty      = view.findViewById(R.id.tv_empty);

        if (!RoleGuard.hasRole("ADMIN")) {
            tvEmpty.setText("Access denied. Admin role required.");
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            view.findViewById(R.id.fab_add_user).setVisibility(View.GONE);
            return;
        }

        viewModel = new androidx.lifecycle.ViewModelProvider(this,
                new UserManagementViewModel.Factory(ServiceLocator.getInstance()))
                .get(UserManagementViewModel.class);

        viewModel.getRegistrationResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            if (result.isSuccess()) {
                Toast.makeText(requireContext(),
                        "User \"" + result.getData().username + "\" created",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(),
                        "Error: " + result.getFirstError(),
                        Toast.LENGTH_LONG).show();
            }
        });

        adapter = new UserAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        String orgId = ServiceLocator.getInstance().getSessionManager().getOrgId();
        if (orgId == null) orgId = "";

        final String finalOrgId = orgId;

        ServiceLocator.getInstance().getUserRepository()
                .getUsersByOrg(orgId)
                .observe(getViewLifecycleOwner(), users -> {
                    if (users == null || users.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        tvEmpty.setVisibility(View.GONE);
                        adapter.submitList(users);
                    }
                });

        view.findViewById(R.id.fab_add_user).setOnClickListener(v ->
                showCreateUserDialog(finalOrgId));
    }

    // ── Create-user dialog ────────────────────────────────────────────────────

    private void showCreateUserDialog(String orgId) {
        // Load zones on a background thread to avoid Room's main-thread restriction
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            List<Zone> zones = ServiceLocator.getInstance().getZoneRepository().getZones(orgId);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> buildCreateUserDialog(orgId, zones));
        });
    }

    private void buildCreateUserDialog(String orgId, List<Zone> zones) {
        int padding = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding * 2, padding, padding * 2, padding);

        EditText etUsername = new EditText(requireContext());
        etUsername.setHint("Username");
        layout.addView(etUsername);

        EditText etPassword = new EditText(requireContext());
        etPassword.setHint("Password (min 12 chars)");
        etPassword.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPassword);

        TextView tvRoleLabel = new TextView(requireContext());
        tvRoleLabel.setText("Role");
        tvRoleLabel.setPadding(0, padding, 0, 0);
        layout.addView(tvRoleLabel);

        Spinner spinnerRole = new Spinner(requireContext());
        String[] roles = {"WORKER", "DISPATCHER", "COMPLIANCE_REVIEWER", "ADMIN"};
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, roles);
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(roleAdapter);
        layout.addView(spinnerRole);

        // Zone picker (visible only when WORKER role is selected)
        TextView tvZoneLabel = new TextView(requireContext());
        tvZoneLabel.setText("Zone");
        tvZoneLabel.setPadding(0, padding, 0, 0);
        layout.addView(tvZoneLabel);

        Spinner spinnerZone = new Spinner(requireContext());
        String[] zoneNames = new String[zones.size() + 1];
        String[] zoneIds   = new String[zones.size() + 1];
        zoneNames[0] = "(No zone)";
        zoneIds[0]   = null;
        for (int i = 0; i < zones.size(); i++) {
            zoneNames[i + 1] = zones.get(i).name;
            zoneIds[i + 1]   = zones.get(i).id;
        }
        ArrayAdapter<String> zoneAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, zoneNames);
        zoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerZone.setAdapter(zoneAdapter);
        layout.addView(spinnerZone);

        // Show/hide zone picker based on role selection
        tvZoneLabel.setVisibility(View.VISIBLE);
        spinnerZone.setVisibility(View.VISIBLE);
        spinnerRole.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                boolean isWorker = "WORKER".equals(roles[position]);
                tvZoneLabel.setVisibility(isWorker ? View.VISIBLE : View.GONE);
                spinnerZone.setVisibility(isWorker ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        new AlertDialog.Builder(requireContext())
                .setTitle("Create User")
                .setView(layout)
                .setPositiveButton("Create", (dialog, which) -> {
                    String username = etUsername.getText().toString().trim();
                    String password = etPassword.getText().toString();
                    String role     = (String) spinnerRole.getSelectedItem();
                    String zoneId   = zoneIds[spinnerZone.getSelectedItemPosition()];

                    viewModel.registerUser(orgId, username, password, role,
                            "WORKER".equals(role) ? zoneId : null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Inner adapter ─────────────────────────────────────────────────────────

    static class UserAdapter extends ListAdapter<User, UserAdapter.ViewHolder> {

        private static final DiffUtil.ItemCallback<User> DIFF = new DiffUtil.ItemCallback<User>() {
            @Override public boolean areItemsTheSame(@NonNull User a, @NonNull User b) {
                return a.id.equals(b.id);
            }
            @Override public boolean areContentsTheSame(@NonNull User a, @NonNull User b) {
                return a.username.equals(b.username) && a.role.equals(b.role)
                        && a.isActive == b.isActive;
            }
        };

        UserAdapter() { super(DIFF); }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Build a simple two-row card programmatically; no extra layout file needed.
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setOrientation(LinearLayout.VERTICAL);

            int dp8  = (int) (8  * parent.getContext().getResources().getDisplayMetrics().density);
            int dp4  = (int) (4  * parent.getContext().getResources().getDisplayMetrics().density);
            root.setPadding(dp8 * 2, dp8, dp8 * 2, dp8);

            TextView tvName = new TextView(parent.getContext());
            tvName.setId(android.R.id.text1);
            tvName.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
            root.addView(tvName);

            TextView tvRole = new TextView(parent.getContext());
            tvRole.setId(android.R.id.text2);
            tvRole.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
            tvRole.setPadding(0, dp4, 0, 0);
            root.addView(tvRole);

            // Card wrapper
            com.google.android.material.card.MaterialCardView card =
                    new com.google.android.material.card.MaterialCardView(parent.getContext());
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp4, 0, dp4);
            card.setLayoutParams(lp);
            card.setCardElevation(dp4 / 2f);
            card.addView(root);

            return new ViewHolder(card, tvName, tvRole);
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
            h.bind(getItem(pos));
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvName;
            private final TextView tvRole;

            ViewHolder(View root, TextView tvName, TextView tvRole) {
                super(root);
                this.tvName = tvName;
                this.tvRole = tvRole;
            }

            void bind(User user) {
                tvName.setText(user.username);
                String roleStatus = user.role + (user.isActive ? "" : " • Inactive");
                tvRole.setText(roleStatus);
            }
        }
    }
}
