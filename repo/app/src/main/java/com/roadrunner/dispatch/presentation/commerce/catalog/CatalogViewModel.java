package com.roadrunner.dispatch.presentation.commerce.catalog;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.core.domain.repository.ProductRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the product catalog screen.
 *
 * <p>Exposes a reactive product list driven by the Room-backed
 * {@link ProductRepository}. Search re-routes to the filtered LiveData
 * query while a null/empty query falls back to all active products.
 */
public class CatalogViewModel extends ViewModel {

    private final ProductRepository productRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Currently active product list (all active, or search results). */
    private final MutableLiveData<LiveData<List<Product>>> currentProducts = new MutableLiveData<>();

    /** Convenience flat view — set by the Fragment after switching source. */
    private LiveData<List<Product>> products;

    /** Error message for UI display. */
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public CatalogViewModel(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Load all active products for the given org.
     * Call once from the Fragment after the session org ID is available.
     */
    public LiveData<List<Product>> getProducts(String orgId) {
        if (products == null) {
            products = productRepository.getActiveProducts(orgId);
        }
        return products;
    }

    /**
     * Filter the product list by {@code query}.
     * An empty or null query resets to all active products.
     *
     * @return LiveData for the (possibly filtered) product list
     */
    public LiveData<List<Product>> search(String orgId, String query) {
        if (query == null || query.trim().isEmpty()) {
            products = productRepository.getActiveProducts(orgId);
        } else {
            products = productRepository.searchProducts(orgId, query.trim());
        }
        return products;
    }

    public LiveData<String> getError() {
        return error;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
