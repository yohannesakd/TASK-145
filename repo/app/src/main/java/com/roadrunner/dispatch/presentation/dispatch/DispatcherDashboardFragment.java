package com.roadrunner.dispatch.presentation.dispatch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

/**
 * Dispatcher dashboard showing open-task count, active-worker count, and zone count.
 * Each card navigates to the corresponding list screen.
 */
public class DispatcherDashboardFragment extends Fragment {

    private static final String ARG_ORG_ID  = "org_id";
    private static final String ARG_USER_ID = "user_id";

    public DispatcherDashboardFragment() {}

    public static DispatcherDashboardFragment newInstance(String orgId, String userId) {
        DispatcherDashboardFragment f = new DispatcherDashboardFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORG_ID, orgId);
        args.putString(ARG_USER_ID, userId);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dispatcher_dashboard, container, false);
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

        String orgId = getArguments() != null ? getArguments().getString(ARG_ORG_ID, "") : "";

        NavController nav = Navigation.findNavController(view);

        view.findViewById(R.id.card_open_tasks).setOnClickListener(v -> {
            Bundle tasksArgs = new Bundle();
            tasksArgs.putString("org_id", orgId);
            tasksArgs.putBoolean("is_dispatcher", true);
            nav.navigate(R.id.action_dispatcher_to_tasks, tasksArgs);
        });

        view.findViewById(R.id.card_zones).setOnClickListener(v -> {
            Bundle zonesArgs = new Bundle();
            zonesArgs.putString("org_id", orgId);
            nav.navigate(R.id.action_dispatcher_to_zones, zonesArgs);
        });

        // card_active_workers would navigate to a worker list — wired when available
    }
}
