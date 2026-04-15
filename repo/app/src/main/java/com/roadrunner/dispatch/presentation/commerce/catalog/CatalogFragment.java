package com.roadrunner.dispatch.presentation.commerce.catalog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import androidx.appcompat.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import java.util.List;

/**
 * Fragment that displays the product catalog in a 2-column grid.
 *
 * <p>Provides a {@link SearchView} that filters the product list in real time.
 * Tapping "Add" on a product always opens a dialog so the operator must
 * explicitly identify the customer. The operator's own session ID is never
 * used as a customer surrogate.
 */
public class CatalogFragment extends Fragment {

    private static final int GRID_SPAN = 2;

    private CatalogViewModel viewModel;
    private ProductAdapter adapter;
    private SessionManager sessionManager;

    private SearchView searchView;
    private RecyclerView recyclerProducts;
    private ProgressBar progressCatalog;
    private TextView textEmpty;

    // ── Fragment lifecycle ────────────────────────────────────────────────────

    public CatalogFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_catalog, container, false);
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

        // Resolve dependencies
        ServiceLocator serviceLocator = ServiceLocator.getInstance();
        sessionManager = serviceLocator.getSessionManager();

        // Create ViewModel
        CatalogViewModelFactory factory = new CatalogViewModelFactory(serviceLocator);
        viewModel = new ViewModelProvider(this, factory).get(CatalogViewModel.class);

        // Bind views
        searchView       = view.findViewById(R.id.search_view);
        recyclerProducts = view.findViewById(R.id.recycler_products);
        progressCatalog  = view.findViewById(R.id.progress_catalog);
        textEmpty        = view.findViewById(R.id.text_empty);

        // Set up RecyclerView
        adapter = new ProductAdapter(this::onAddToCartClicked);
        recyclerProducts.setLayoutManager(new GridLayoutManager(requireContext(), GRID_SPAN));
        recyclerProducts.setAdapter(adapter);

        // Load products for current org
        String orgId = sessionManager.getOrgId();
        observeProducts(viewModel.getProducts(orgId));

        // Wire up search
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                observeProducts(viewModel.search(orgId, query));
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                observeProducts(viewModel.search(orgId, newText));
                return true;
            }
        });

        // Observe errors
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                textEmpty.setText(error);
                textEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LiveData<List<Product>> currentProductSource;

    /**
     * Swap the LiveData source being observed for the product list.
     * Removes prior observer to prevent duplicate callbacks.
     */
    private void observeProducts(LiveData<List<Product>> liveData) {
        if (currentProductSource != null) {
            currentProductSource.removeObservers(getViewLifecycleOwner());
        }
        currentProductSource = liveData;
        liveData.observe(getViewLifecycleOwner(), products -> {
            progressCatalog.setVisibility(View.GONE);

            if (products == null || products.isEmpty()) {
                textEmpty.setVisibility(View.VISIBLE);
                recyclerProducts.setVisibility(View.GONE);
            } else {
                textEmpty.setVisibility(View.GONE);
                recyclerProducts.setVisibility(View.VISIBLE);
                adapter.submitList(products);
            }
        });
    }

    /**
     * Handle "Add to cart" tap.
     *
     * <p>Always shows the customer dialog so the operator must explicitly
     * specify who the order is for. The operator's own session ID is never
     * used as the customer identifier. The store ID is derived from the org
     * (each org acts as an independent store).
     */
    private void onAddToCartClicked(Product product) {
        String orgId = sessionManager.getOrgId();
        showCustomerDialog(product, orgId);
    }

    /**
     * Show a simple dialog asking for the customer name when no session customer
     * is available. The entered name is used as the customer ID.
     */
    private void showCustomerDialog(Product product, String orgId) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Customer name / ID");
        input.setSingleLine(true);

        new AlertDialog.Builder(requireContext())
                .setTitle("Who is this order for?")
                .setView(input)
                .setPositiveButton("Continue", (dialog, which) -> {
                    String customerId = input.getText() != null
                            ? input.getText().toString().trim() : "";
                    if (!customerId.isEmpty()) {
                        String userId = sessionManager.getUserId();
                        String storeId = orgId != null ? orgId : "";
                        navigateToCart(product, customerId, storeId, orgId, userId);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Navigate to the cart screen, passing the selected product so the
     * CartFragment can immediately add it via CartViewModel.
     *
     * <p>Arguments are bundled so CartFragment can call {@code addToCart}
     * without further user input.
     */
    private void navigateToCart(Product product, String customerId,
                                String storeId, String orgId, String userId) {
        Bundle args = new Bundle();
        args.putString("customerId",  customerId);
        args.putString("storeId",     storeId);
        args.putString("productId",   product.id);
        args.putString("orgId",       orgId);
        args.putString("userId",      userId);

        View root = getView();
        if (root != null) {
            Navigation.findNavController(root)
                    .navigate(R.id.action_catalog_to_cart, args);
        }
    }
}
