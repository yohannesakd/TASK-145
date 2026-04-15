package com.roadrunner.dispatch.presentation.commerce.catalog;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.presentation.common.ImageCache;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RecyclerView adapter for the product catalog grid.
 *
 * <p>Uses {@link ListAdapter} with {@link DiffUtil} to efficiently update the
 * displayed list when the underlying data changes. Each item exposes an
 * "Add to cart" button which delegates to the {@link OnAddToCartListener}
 * provided at construction time.
 */
public class ProductAdapter extends ListAdapter<Product, ProductAdapter.ViewHolder> {

    /** Callback interface for add-to-cart actions. */
    public interface OnAddToCartListener {
        void onAddToCart(Product product);
    }

    private final OnAddToCartListener listener;
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ProductAdapter(OnAddToCartListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<Product> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Product>() {

                @Override
                public boolean areItemsTheSame(@NonNull Product oldItem, @NonNull Product newItem) {
                    return oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Product oldItem, @NonNull Product newItem) {
                    return oldItem.name.equals(newItem.name)
                            && oldItem.unitPriceCents == newItem.unitPriceCents;
                }
            };

    // ── Adapter overrides ─────────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product product = getItem(position);
        holder.bind(product, listener, imageExecutor, mainHandler);
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView imageProduct;
        private final TextView textName;
        private final TextView textBrandModel;
        private final TextView textPrice;
        private final MaterialButton btnAddToCart;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageProduct  = itemView.findViewById(R.id.image_product);
            textName      = itemView.findViewById(R.id.text_name);
            textBrandModel = itemView.findViewById(R.id.text_brand_model);
            textPrice     = itemView.findViewById(R.id.text_price);
            btnAddToCart  = itemView.findViewById(R.id.btn_add_to_cart);
        }

        void bind(@NonNull Product product, @NonNull OnAddToCartListener listener,
                  @NonNull ExecutorService executor, @NonNull Handler mainHandler) {
            textName.setText(product.name);

            // Assemble brand / series / model into a single subtitle string.
            StringBuilder subtitle = new StringBuilder();
            if (product.brand != null && !product.brand.isEmpty()) {
                subtitle.append(product.brand);
            }
            if (product.series != null && !product.series.isEmpty()) {
                if (subtitle.length() > 0) subtitle.append(" \u00B7 ");
                subtitle.append(product.series);
            }
            if (product.model != null && !product.model.isEmpty()) {
                if (subtitle.length() > 0) subtitle.append(" \u00B7 ");
                subtitle.append(product.model);
            }
            textBrandModel.setText(subtitle.toString());
            textBrandModel.setVisibility(subtitle.length() > 0 ? View.VISIBLE : View.GONE);

            // Format price from cents to "$X.XX".
            double dollars = product.unitPriceCents / 100.0;
            textPrice.setText(String.format(Locale.getDefault(), "$%.2f", dollars));

            // Load image from ImageCache if a URI is present; otherwise show placeholder.
            imageProduct.setImageResource(R.drawable.ic_launcher_foreground);
            if (product.imageUri != null && !product.imageUri.isEmpty()) {
                final String uri = product.imageUri;
                // Tag the view so stale async results can be discarded on recycle.
                imageProduct.setTag(uri);

                ImageCache cache = ImageCache.getInstance();
                Bitmap cached = cache.get(uri);
                if (cached != null) {
                    imageProduct.setImageBitmap(cached);
                } else {
                    executor.execute(() -> {
                        Bitmap bmp = null;
                        try {
                            Uri parsed = Uri.parse(uri);
                            InputStream is = itemView.getContext().getContentResolver()
                                    .openInputStream(parsed);
                            if (is != null) {
                                // Decode to a 256×256 thumbnail to limit memory use.
                                bmp = ImageCache.decodeSampled(is, 256, 256);
                                is.close();
                                if (bmp != null) {
                                    cache.put(uri, bmp);
                                }
                            }
                        } catch (IOException | SecurityException ignored) {
                            // URI unreachable — placeholder will remain.
                        }
                        final Bitmap result = bmp;
                        mainHandler.post(() -> {
                            // Only apply if this view still shows the same product.
                            if (uri.equals(imageProduct.getTag()) && result != null) {
                                imageProduct.setImageBitmap(result);
                            }
                        });
                    });
                }
            }

            btnAddToCart.setOnClickListener(v -> listener.onAddToCart(product));
        }
    }
}
