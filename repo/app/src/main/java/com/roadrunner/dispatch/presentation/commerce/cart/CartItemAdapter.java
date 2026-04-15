package com.roadrunner.dispatch.presentation.commerce.cart;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.CartItem;

import java.util.Locale;

/**
 * RecyclerView adapter for the shopping cart item list.
 *
 * <p>Supports:
 * <ul>
 *   <li>Quantity increment / decrement (callbacks to the fragment).
 *   <li>Item removal.
 *   <li>Price-conflict UI: when {@link CartItem#conflictFlag} is true, shows an amber
 *       warning row with the original vs. snapshot price and a "Resolve" button.
 * </ul>
 */
public class CartItemAdapter extends ListAdapter<CartItem, CartItemAdapter.ViewHolder> {

    // ── Callback interfaces ───────────────────────────────────────────────────

    public interface OnQuantityChangeListener {
        void onIncrease(CartItem item);
        void onDecrease(CartItem item);
    }

    public interface OnRemoveListener {
        void onRemove(CartItem item);
    }

    public interface OnResolveListener {
        void onResolve(CartItem item);
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final OnQuantityChangeListener quantityListener;
    private final OnRemoveListener removeListener;
    private final OnResolveListener resolveListener;

    /** Product names keyed by productId — populated by the fragment for display. */
    private java.util.Map<String, String> productNames = new java.util.HashMap<>();

    public CartItemAdapter(OnQuantityChangeListener quantityListener,
                           OnRemoveListener removeListener,
                           OnResolveListener resolveListener) {
        super(DIFF_CALLBACK);
        this.quantityListener = quantityListener;
        this.removeListener   = removeListener;
        this.resolveListener  = resolveListener;
    }

    /** Update the product-name lookup table used for display. */
    public void setProductNames(java.util.Map<String, String> names) {
        this.productNames = names;
        notifyItemRangeChanged(0, getItemCount());
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<CartItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CartItem>() {

                @Override
                public boolean areItemsTheSame(@NonNull CartItem oldItem, @NonNull CartItem newItem) {
                    return oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull CartItem oldItem, @NonNull CartItem newItem) {
                    return oldItem.quantity == newItem.quantity
                            && oldItem.unitPriceSnapshotCents == newItem.unitPriceSnapshotCents
                            && oldItem.conflictFlag == newItem.conflictFlag;
                }
            };

    // ── Adapter overrides ─────────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CartItem item = getItem(position);
        String productName = productNames.containsKey(item.productId)
                ? productNames.get(item.productId)
                : item.productId;
        holder.bind(item, productName, quantityListener, removeListener, resolveListener);
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView      textItemName;
        private final TextView      textUnitPrice;
        private final MaterialButton btnDecrease;
        private final TextView      textQuantity;
        private final MaterialButton btnIncrease;
        private final TextView      textLineTotal;
        private final ImageView     iconConflict;
        private final TextView      textConflict;
        private final MaterialButton btnResolve;
        private final MaterialButton btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textItemName  = itemView.findViewById(R.id.text_item_name);
            textUnitPrice = itemView.findViewById(R.id.text_unit_price);
            btnDecrease   = itemView.findViewById(R.id.btn_decrease);
            textQuantity  = itemView.findViewById(R.id.text_quantity);
            btnIncrease   = itemView.findViewById(R.id.btn_increase);
            textLineTotal = itemView.findViewById(R.id.text_line_total);
            iconConflict  = itemView.findViewById(R.id.icon_conflict);
            textConflict  = itemView.findViewById(R.id.text_conflict);
            btnResolve    = itemView.findViewById(R.id.btn_resolve);
            btnRemove     = itemView.findViewById(R.id.btn_remove);
        }

        void bind(@NonNull CartItem item,
                  @Nullable String productName,
                  @NonNull OnQuantityChangeListener quantityListener,
                  @NonNull OnRemoveListener removeListener,
                  @NonNull OnResolveListener resolveListener) {

            textItemName.setText(productName != null ? productName : item.productId);

            double unitDollars = item.unitPriceSnapshotCents / 100.0;
            textUnitPrice.setText(String.format(Locale.getDefault(), "Unit: $%.2f", unitDollars));

            textQuantity.setText(String.valueOf(item.quantity));

            double lineTotal = (item.unitPriceSnapshotCents * item.quantity) / 100.0;
            textLineTotal.setText(String.format(Locale.getDefault(), "$%.2f", lineTotal));

            // Conflict UI
            if (item.conflictFlag) {
                iconConflict.setVisibility(View.VISIBLE);
                textConflict.setVisibility(View.VISIBLE);
                btnResolve.setVisibility(View.VISIBLE);

                double originalDollars  = item.originalPriceCents / 100.0;
                double snapshotDollars  = item.unitPriceSnapshotCents / 100.0;
                textConflict.setText(String.format(Locale.getDefault(),
                        "Price conflict: $%.2f vs $%.2f", originalDollars, snapshotDollars));

                btnResolve.setOnClickListener(v -> resolveListener.onResolve(item));
            } else {
                iconConflict.setVisibility(View.GONE);
                textConflict.setVisibility(View.GONE);
                btnResolve.setVisibility(View.GONE);
            }

            // Prevent negative quantities
            btnDecrease.setEnabled(item.quantity > 1);

            btnDecrease.setOnClickListener(v -> quantityListener.onDecrease(item));
            btnIncrease.setOnClickListener(v -> quantityListener.onIncrease(item));
            btnRemove.setOnClickListener(v -> removeListener.onRemove(item));
        }
    }
}
