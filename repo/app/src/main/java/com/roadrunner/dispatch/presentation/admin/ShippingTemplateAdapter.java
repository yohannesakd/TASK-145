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
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;

import java.util.Locale;

public class ShippingTemplateAdapter extends ListAdapter<ShippingTemplate, ShippingTemplateAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<ShippingTemplate> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ShippingTemplate>() {
                @Override
                public boolean areItemsTheSame(@NonNull ShippingTemplate oldItem, @NonNull ShippingTemplate newItem) {
                    return oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ShippingTemplate oldItem, @NonNull ShippingTemplate newItem) {
                    return oldItem.name.equals(newItem.name)
                            && oldItem.costCents == newItem.costCents
                            && oldItem.minDays == newItem.minDays
                            && oldItem.maxDays == newItem.maxDays
                            && oldItem.isPickup == newItem.isPickup;
                }
            };

    public ShippingTemplateAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_config_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ShippingTemplate t = getItem(position);
        holder.tvTitle.setText(t.name);
        holder.tvDetail.setText(String.format(Locale.getDefault(),
                "$%.2f — %d-%d days%s",
                t.costCents / 100.0, t.minDays, t.maxDays,
                t.isPickup ? " (Pickup)" : ""));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvDetail;
        ViewHolder(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tv_config_title);
            tvDetail = v.findViewById(R.id.tv_config_detail);
        }
    }
}
