package com.roadrunner.dispatch.presentation.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

/**
 * Admin-only view of all orders for the organization, including DRAFT and FINALIZED.
 */
public class OrderListFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private final OrderAdapter orderAdapter = new OrderAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_order_list, container, false);
        recyclerView = root.findViewById(R.id.recycler_orders);
        tvEmpty = root.findViewById(R.id.tv_empty_orders);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!RoleGuard.hasRole("ADMIN", "DISPATCHER", "COMPLIANCE_REVIEWER")) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Access denied. Admin, Dispatcher, or Compliance Reviewer role required.");
            return;
        }

        String orgId = getArguments() != null ? getArguments().getString("orgId", "") : "";
        if (orgId.isEmpty()) {
            orgId = ServiceLocator.getInstance().getSessionManager().getOrgId();
            if (orgId == null) orgId = "";
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(orderAdapter);

        ServiceLocator.getInstance().getOrderRepository()
                .getOrders(orgId)
                .observe(getViewLifecycleOwner(), orders -> {
                    if (orders == null || orders.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        tvEmpty.setVisibility(View.GONE);
                        orderAdapter.submitList(orders);
                    }
                });
    }
}
