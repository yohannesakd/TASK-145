package com.roadrunner.dispatch.presentation.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

/**
 * Admin dashboard with navigation cards for Products (Catalog), Orders, and Employers.
 * All navigation uses NavController to maintain back-stack and destination guards.
 */
public class AdminDashboardFragment extends Fragment {

    public AdminDashboardFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!RoleGuard.hasRole("ADMIN")) {
            android.widget.TextView tvError = new android.widget.TextView(requireContext());
            tvError.setText("Access denied. Admin role required.");
            tvError.setPadding(32, 32, 32, 32);
            ((ViewGroup) view).addView(tvError);
            return;
        }

        String orgId = ServiceLocator.getInstance().getSessionManager().getOrgId();

        view.findViewById(R.id.card_products).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_admin_to_catalog));

        view.findViewById(R.id.card_orders).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("orgId", orgId);
            Navigation.findNavController(v).navigate(R.id.action_admin_to_orders, args);
        });

        view.findViewById(R.id.card_employers).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_admin_to_employers));

        view.findViewById(R.id.card_users).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_admin_to_users));

        view.findViewById(R.id.card_config).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_admin_to_config));

        view.findViewById(R.id.card_import_export).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_admin_to_import_export));
    }
}
