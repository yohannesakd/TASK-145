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
import com.roadrunner.dispatch.core.domain.model.DiscountRule;

import java.util.Locale;

public class DiscountRuleAdapter extends ListAdapter<DiscountRule, DiscountRuleAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<DiscountRule> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<DiscountRule>() {
                @Override
                public boolean areItemsTheSame(@NonNull DiscountRule oldItem, @NonNull DiscountRule newItem) {
                    return oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull DiscountRule oldItem, @NonNull DiscountRule newItem) {
                    return oldItem.name.equals(newItem.name)
                            && oldItem.type.equals(newItem.type)
                            && Double.compare(oldItem.value, newItem.value) == 0
                            && oldItem.status.equals(newItem.status);
                }
            };

    public DiscountRuleAdapter() {
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
        DiscountRule r = getItem(position);
        holder.tvTitle.setText(r.name);
        String detail = "PERCENT_OFF".equals(r.type)
                ? String.format(Locale.getDefault(), "%.0f%% off", r.value)
                : String.format(Locale.getDefault(), "$%.2f off", r.value);
        holder.tvDetail.setText(detail + " — " + r.status);
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
