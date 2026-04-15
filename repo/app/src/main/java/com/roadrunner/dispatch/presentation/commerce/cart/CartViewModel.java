package com.roadrunner.dispatch.presentation.commerce.cart;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.roadrunner.dispatch.core.domain.model.Cart;
import com.roadrunner.dispatch.core.domain.model.CartItem;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.CartRepository;
import com.roadrunner.dispatch.core.domain.usecase.AddToCartUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ResolveCartConflictUseCase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the shopping-cart screen.
 *
 * <p>Coordinates cart item display, add-to-cart, item removal, and price-conflict
 * resolution. All repository calls execute on a background thread; results are
 * posted back to LiveData observed by the Fragment.
 */
public class CartViewModel extends ViewModel {

    private final CartRepository cartRepository;
    private final AddToCartUseCase addToCartUseCase;
    private final ResolveCartConflictUseCase resolveCartConflictUseCase;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Cart items for the currently loaded cart. */
    private final MutableLiveData<List<CartItem>> cartItems = new MutableLiveData<>();

    /** True while a cart item has at least one unresolved price conflict. */
    private final MutableLiveData<Boolean> hasConflicts = new MutableLiveData<>(false);

    /** Non-null when an operation fails — observed once then cleared by the UI. */
    private final MutableLiveData<String> error = new MutableLiveData<>();

    /** The most recently loaded cart, exposing customerId and storeId to the Fragment. */
    private final MutableLiveData<Cart> loadedCart = new MutableLiveData<>();

    /** ID of the cart currently displayed. */
    private String currentCartId;

    public CartViewModel(CartRepository cartRepository,
                         AddToCartUseCase addToCartUseCase,
                         ResolveCartConflictUseCase resolveCartConflictUseCase) {
        this.cartRepository = cartRepository;
        this.addToCartUseCase = addToCartUseCase;
        this.resolveCartConflictUseCase = resolveCartConflictUseCase;
    }

    // ── Exposed LiveData ──────────────────────────────────────────────────────

    public LiveData<List<CartItem>> getCartItems() { return cartItems; }
    public LiveData<Boolean> getHasConflicts() { return hasConflicts; }
    public LiveData<String> getError() { return error; }
    public LiveData<Cart> getLoadedCart() { return loadedCart; }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Add a product to the cart (or merge if already present).
     * Refreshes the item list and conflict flag afterwards.
     */
    public void addToCart(String customerId, String storeId, String productId,
                          int quantity, String orgId, String userId) {
        executor.execute(() -> {
            Result<CartItem> result = addToCartUseCase.execute(
                    customerId, storeId, productId, quantity, orgId, userId);
            if (result.isSuccess()) {
                // Derive cart ID from the returned item for subsequent refreshes
                CartItem item = result.getData();
                currentCartId = item.cartId;
                refreshCartItems(item.cartId);
            } else {
                error.postValue(result.getFirstError());
            }
        });
    }

    /**
     * Load or reload items for the given cart.
     * Safe to call from any thread.
     */
    public void loadCart(String cartId) {
        this.currentCartId = cartId;
        executor.execute(() -> refreshCartItems(cartId));
    }

    /**
     * Find the active cart for the given (orgId, customerId, storeId) triple and load its items.
     *
     * <p>Intended for the case where CartFragment is opened directly (not from CatalogFragment),
     * so there is no product to add and no cartId in navigation args. If no active cart exists
     * the item list is posted as {@code null} / empty, which the UI renders as an empty cart.
     */
    public void loadActiveCart(String orgId, String customerId, String storeId) {
        executor.execute(() -> {
            Cart cart = cartRepository.findActiveCart(customerId, storeId, orgId);
            if (cart != null) {
                currentCartId = cart.id;
                loadedCart.postValue(cart);
                refreshCartItems(cart.id);
            }
        });
    }

    /**
     * Find the most recent cart created by this operator and load its items.
     * Used for direct navigation (bottom-nav shortcut) where the operator
     * userId is not the same as the cart's customerId.
     */
    public void loadCartByCreator(String orgId, String createdBy) {
        executor.execute(() -> {
            Cart cart = cartRepository.findMostRecentByCreator(createdBy, orgId);
            if (cart != null) {
                currentCartId = cart.id;
                loadedCart.postValue(cart);
                refreshCartItems(cart.id);
            }
        });
    }

    /**
     * Decrease the quantity of a cart item by 1.
     * Has no effect if the item's current quantity is already 1 (the UI disables
     * the button in that case, but this guard makes the method safe to call directly).
     */
    public void decreaseQuantity(CartItem item) {
        if (item.quantity <= 1) return;
        CartItem updated = new CartItem(
                item.id,
                item.cartId,
                item.productId,
                item.quantity - 1,
                item.unitPriceSnapshotCents,
                item.conflictFlag,
                item.originalPriceCents);
        executor.execute(() -> {
            cartRepository.updateCartItem(updated);
            if (currentCartId != null) refreshCartItems(currentCartId);
        });
    }

    /**
     * Remove a single item from the cart by its item ID.
     */
    public void removeItem(String itemId) {
        executor.execute(() -> {
            cartRepository.deleteCartItem(itemId);
            if (currentCartId != null) refreshCartItems(currentCartId);
        });
    }

    /**
     * Resolve a price conflict for {@code productId} in the current cart.
     * {@code chosenPriceCents} is the unit price the customer selects.
     */
    public void resolveConflict(String cartId, String productId, long chosenPriceCents) {
        executor.execute(() -> {
            Result<CartItem> result =
                    resolveCartConflictUseCase.execute(cartId, productId, chosenPriceCents);
            if (result.isSuccess()) {
                refreshCartItems(cartId);
            } else {
                error.postValue(result.getFirstError());
            }
        });
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Must be called on the executor thread. */
    private void refreshCartItems(String cartId) {
        List<CartItem> items = cartRepository.getCartItems(cartId);
        cartItems.postValue(items);

        int conflictCount = cartRepository.getConflictCount(cartId);
        hasConflicts.postValue(conflictCount > 0);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
