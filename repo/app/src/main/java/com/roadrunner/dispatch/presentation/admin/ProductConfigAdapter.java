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
import com.roadrunner.dispatch.core.domain.model.Product;

import java.util.Locale;

public class ProductConfigAdapter extends ListAdapter<Product, ProductConfigAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<Product> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Product>() {
                @Override
                public boolean areItemsTheSame(@NonNull Product oldItem, @NonNull Product newItem) {
                    return oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Product oldItem, @NonNull Product newItem) {
                    return oldItem.name.equals(newItem.name)
                            && oldItem.unitPriceCents == newItem.unitPriceCents
                            && oldItem.regulated == newItem.regulated
                            && (oldItem.brand == null ? newItem.brand == null
                                    : oldItem.brand.equals(newItem.brand));
                }
            };

    public ProductConfigAdapter() {
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
        Product p = getItem(position);
        holder.tvTitle.setText(p.name);
        String detail = String.format(Locale.getDefault(), "$%.2f", p.unitPriceCents / 100.0);
        if (p.brand != null && !p.brand.isEmpty()) {
            detail = p.brand + " — " + detail;
        }
        if (p.regulated) {
            detail = detail + " (Regulated)";
        }
        holder.tvDetail.setText(detail);
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
