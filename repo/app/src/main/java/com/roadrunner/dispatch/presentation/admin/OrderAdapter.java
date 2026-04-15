package com.roadrunner.dispatch.presentation.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.Order;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OrderAdapter extends ListAdapter<Order, OrderAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<Order> DIFF = new DiffUtil.ItemCallback<Order>() {
        @Override public boolean areItemsTheSame(@NonNull Order a, @NonNull Order b) {
            return a.id.equals(b.id);
        }
        @Override public boolean areContentsTheSame(@NonNull Order a, @NonNull Order b) {
            return a.status.equals(b.status) && a.totalCents == b.totalCents;
        }
    };

    public OrderAdapter() {
        super(DIFF);
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order_row, parent, false);
        return new ViewHolder(v);
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        h.bind(getItem(pos));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvOrderId, tvCustomer, tvTotal, tvStatus, tvDate;

        ViewHolder(View v) {
            super(v);
            tvOrderId = v.findViewById(R.id.tv_order_id);
            tvCustomer = v.findViewById(R.id.tv_order_customer);
            tvTotal = v.findViewById(R.id.tv_order_total);
            tvStatus = v.findViewById(R.id.tv_order_status);
            tvDate = v.findViewById(R.id.tv_order_date);
        }

        void bind(Order order) {
            tvOrderId.setText("Order #" + order.id.substring(0, 8).toUpperCase());
            tvCustomer.setText(order.customerId);
            tvTotal.setText(String.format(Locale.US, "$%.2f", order.totalCents / 100.0));
            tvStatus.setText(order.status);
            if (order.totalsComputedAt > 0) {
                tvDate.setText(new SimpleDateFormat("MMM d, yyyy", Locale.US)
                        .format(new Date(order.totalsComputedAt)));
            } else {
                tvDate.setText("Draft");
            }
        }
    }
}
