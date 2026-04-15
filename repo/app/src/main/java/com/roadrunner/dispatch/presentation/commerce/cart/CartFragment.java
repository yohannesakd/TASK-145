package com.roadrunner.dispatch.presentation.commerce.cart;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.CartItem;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import java.util.List;
import java.util.Locale;

/**
 * Fragment that displays the contents of the current user's cart.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Load the cart for the signed-in user on first view creation.
 *   <li>Display a conflict-warning banner when any item has a price conflict.
 *   <li>Allow quantity adjustments and item removal.
 *   <li>Show a "Resolve" dialog for conflicted items.
 *   <li>Navigate to {@link com.roadrunner.dispatch.presentation.commerce.checkout.CheckoutFragment}
 *       via the "Proceed to Checkout" button.
 * </ul>
 *
 * <p>Expects optional arguments set by {@link com.roadrunner.dispatch.presentation.commerce.catalog.CatalogFragment}:
 * <ul>
 *   <li>{@code customerId} — customer ID to use when adding a product
 *   <li>{@code storeId}    — store ID
 *   <li>{@code productId}  — product to add immediately on arrival
 *   <li>{@code orgId}      — org scope
 *   <li>{@code userId}     — acting user
 * </ul>
 */
public class CartFragment extends Fragment {

    private CartViewModel viewModel;
    private CartItemAdapter adapter;
    private SessionManager sessionManager;

    private CardView     bannerConflict;
    private RecyclerView recyclerCartItems;
    private TextView     textSubtotal;
    private MaterialButton btnCheckout;
    private TextView     textEmptyCart;

    /** Cart ID determined after first add or loaded from a stored session value. */
    private String currentCartId;

    /**
     * The real customer ID for the cart currently displayed.
     * Set from navigation args (catalog flow) or falls back to the operator's
     * user ID as a lookup key when navigating directly to cart.
     */
    private String activeCustomerId;

    /**
     * The store scope for the cart currently displayed.
     * Mirrors the orgId in the current session model (each org IS a store).
     */
    private String activeStoreId;

    // ── Fragment lifecycle ────────────────────────────────────────────────────

    public CartFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!RoleGuard.hasRole("ADMIN", "DISPATCHER", "WORKER")) {
            android.widget.TextView tvDenied = new android.widget.TextView(requireContext());
            tvDenied.setText("Access denied.");
            tvDenied.setPadding(32, 32, 32, 32);
            ((ViewGroup) view).addView(tvDenied);
            return;
        }

        ServiceLocator serviceLocator = ServiceLocator.getInstance();
        sessionManager = serviceLocator.getSessionManager();

        CartViewModelFactory factory = new CartViewModelFactory(serviceLocator);
        viewModel = new ViewModelProvider(this, factory).get(CartViewModel.class);

        // Bind views
        bannerConflict   = view.findViewById(R.id.banner_conflict);
        recyclerCartItems = view.findViewById(R.id.recycler_cart_items);
        textSubtotal     = view.findViewById(R.id.text_subtotal);
        btnCheckout      = view.findViewById(R.id.btn_checkout);
        textEmptyCart    = view.findViewById(R.id.text_empty_cart);

        // Set up RecyclerView
        adapter = new CartItemAdapter(
                new CartItemAdapter.OnQuantityChangeListener() {
                    @Override
                    public void onIncrease(CartItem item) {
                        onIncreaseQuantity(item);
                    }

                    @Override
                    public void onDecrease(CartItem item) {
                        onDecreaseQuantity(item);
                    }
                },
                this::onRemoveItem,
                this::onResolveConflict);
        recyclerCartItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerCartItems.setAdapter(adapter);

        // Observe LiveData
        viewModel.getCartItems().observe(getViewLifecycleOwner(), this::renderCartItems);
        viewModel.getHasConflicts().observe(getViewLifecycleOwner(), hasConflicts ->
                bannerConflict.setVisibility(Boolean.TRUE.equals(hasConflicts)
                        ? View.VISIBLE : View.GONE));
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                textEmptyCart.setText(error);
                textEmptyCart.setVisibility(View.VISIBLE);
            }
        });
        viewModel.getLoadedCart().observe(getViewLifecycleOwner(), cart -> {
            if (cart != null) {
                activeCustomerId = cart.customerId;
                activeStoreId    = cart.storeId;
            }
        });

        // If the catalog passed a product to add immediately, do it now.
        // Otherwise load whatever active cart already exists for this session's context.
        Bundle args = getArguments();
        if (args != null && args.containsKey("productId")) {
            activeCustomerId = args.getString("customerId", "");
            activeStoreId    = args.getString("storeId",    "");
            String productId = args.getString("productId",  "");
            String orgId     = args.getString("orgId",      sessionManager.getOrgId());
            String userId    = args.getString("userId",     sessionManager.getUserId());

            viewModel.addToCart(activeCustomerId, activeStoreId, productId, 1, orgId, userId);
        } else {
            // Direct navigation — no product context (e.g. from a bottom-nav shortcut).
            // Look up the most recent cart this operator created so we find the
            // correct customer/store context regardless of which customer was selected.
            String orgId  = sessionManager.getOrgId();
            String userId = sessionManager.getUserId();
            activeCustomerId = userId;
            activeStoreId    = orgId;
            viewModel.loadCartByCreator(orgId, userId);
        }

        // Checkout navigation
        btnCheckout.setOnClickListener(v -> {
            if (currentCartId != null) {
                Bundle navArgs = new Bundle();
                navArgs.putString("cartId", currentCartId);
                Navigation.findNavController(v)
                        .navigate(R.id.action_cart_to_checkout, navArgs);
            }
        });
    }

    // ── Cart item callbacks ───────────────────────────────────────────────────

    private void onIncreaseQuantity(CartItem item) {
        // Re-add with quantity 1 to increment (use-case merges duplicate productIds).
        // Use the activeCustomerId/activeStoreId captured when the cart was first loaded
        // so the increment targets the same cart that is currently displayed.
        String orgId  = sessionManager.getOrgId()  != null ? sessionManager.getOrgId()  : "";
        String userId = sessionManager.getUserId() != null ? sessionManager.getUserId() : "";
        viewModel.addToCart(activeCustomerId, activeStoreId, item.productId, 1, orgId, userId);
    }

    private void onDecreaseQuantity(CartItem item) {
        viewModel.decreaseQuantity(item);
    }

    private void onRemoveItem(CartItem item) {
        viewModel.removeItem(item.id);
    }

    private void onResolveConflict(CartItem item) {
        if (currentCartId == null) return;

        // Offer the user the choice between the snapshot price and the original price.
        double snapshotDollars = item.unitPriceSnapshotCents / 100.0;
        double originalDollars = item.originalPriceCents      / 100.0;

        String[] options = {
                String.format(Locale.getDefault(), "Keep cart price ($%.2f)", snapshotDollars),
                String.format(Locale.getDefault(), "Use current catalog price ($%.2f)", originalDollars)
        };

        new AlertDialog.Builder(requireContext())
                .setTitle("Resolve Price Conflict")
                .setItems(options, (dialog, which) -> {
                    long chosenCents = (which == 0)
                            ? item.unitPriceSnapshotCents
                            : item.originalPriceCents;
                    viewModel.resolveConflict(currentCartId, item.productId, chosenCents);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Render helpers ────────────────────────────────────────────────────────

    private void renderCartItems(List<CartItem> items) {
        if (items == null || items.isEmpty()) {
            textEmptyCart.setVisibility(View.VISIBLE);
            recyclerCartItems.setVisibility(View.GONE);
            btnCheckout.setEnabled(false);
            textSubtotal.setText("Subtotal: $0.00");
            return;
        }

        textEmptyCart.setVisibility(View.GONE);
        recyclerCartItems.setVisibility(View.VISIBLE);
        btnCheckout.setEnabled(true);

        // Derive the cart ID from the first item for navigation purposes.
        currentCartId = items.get(0).cartId;

        adapter.submitList(items);

        // Compute and display the subtotal.
        long subtotalCents = 0;
        for (CartItem item : items) {
            subtotalCents += item.unitPriceSnapshotCents * item.quantity;
        }
        double subtotalDollars = subtotalCents / 100.0;
        textSubtotal.setText(
                String.format(Locale.getDefault(), "Subtotal: $%.2f", subtotalDollars));
    }
}
