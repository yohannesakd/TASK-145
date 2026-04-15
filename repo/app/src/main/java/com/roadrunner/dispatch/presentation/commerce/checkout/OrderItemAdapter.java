package com.roadrunner.dispatch.presentation.commerce.checkout;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.OrderItem;

import java.util.Locale;

/**
 * Read-only RecyclerView adapter for displaying order line items in the
 * checkout and invoice screens.
 */
public class OrderItemAdapter extends ListAdapter<OrderItem, OrderItemAdapter.ViewHolder> {

    public OrderItemAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<OrderItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<OrderItem>() {

                @Override
                public boolean areItemsTheSame(@NonNull OrderItem a, @NonNull OrderItem b) {
                    return a.id.equals(b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull OrderItem a, @NonNull OrderItem b) {
                    return a.quantity == b.quantity
                            && a.unitPriceCents == b.unitPriceCents
                            && a.lineTotalCents == b.lineTotalCents;
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order_line, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView textProductName;
        private final TextView textQtyAndPrice;
        private final TextView textLineTotal;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textProductName = itemView.findViewById(R.id.text_order_product_name);
            textQtyAndPrice = itemView.findViewById(R.id.text_order_qty_price);
            textLineTotal   = itemView.findViewById(R.id.text_order_line_total);
        }

        void bind(@NonNull OrderItem item) {
            textProductName.setText(item.productName);

            double unitDollars = item.unitPriceCents / 100.0;
            textQtyAndPrice.setText(String.format(Locale.getDefault(),
                    "%d x $%.2f", item.quantity, unitDollars));

            double lineDollars = item.lineTotalCents / 100.0;
            textLineTotal.setText(String.format(Locale.getDefault(), "$%.2f", lineDollars));
        }
    }
}
