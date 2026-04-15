package com.roadrunner.dispatch;

import com.roadrunner.dispatch.core.domain.model.Cart;
import com.roadrunner.dispatch.core.domain.model.CartItem;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.CartRepository;
import com.roadrunner.dispatch.core.domain.usecase.ResolveCartConflictUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ResolveCartConflictUseCaseTest {

    private StubCartRepository cartRepo;
    private ResolveCartConflictUseCase useCase;

    @Before
    public void setUp() {
        cartRepo = new StubCartRepository();
        useCase  = new ResolveCartConflictUseCase(cartRepo);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** A cart item that has a conflict flag set. */
    private CartItem conflictedItem(String cartId, String productId,
                                    long snapshotPrice, long originalPrice) {
        return new CartItem("item-" + productId, cartId, productId, 1,
                snapshotPrice, true /* conflictFlag */, originalPrice);
    }

    /** A cart item with NO conflict. */
    private CartItem clearItem(String cartId, String productId, long price) {
        return new CartItem("item-" + productId, cartId, productId, 1,
                price, false, 0);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void resolveConflict_conflictFlagClearedAndChosenPriceSet() {
        CartItem item = conflictedItem("c1", "p1", 800L, 1000L);
        cartRepo.storeItem("c1", "p1", item);

        // Choose the original price (1000L) — must be either snapshot or original
        Result<CartItem> result = useCase.execute("c1", "p1", 1000L);

        assertTrue(result.isSuccess());
        CartItem resolved = result.getData();
        assertFalse(resolved.conflictFlag);
        assertEquals(1000L, resolved.unitPriceSnapshotCents);
    }

    @Test
    public void resolveConflict_arbitraryPrice_rejected() {
        CartItem item = conflictedItem("c1", "p1", 800L, 1000L);
        cartRepo.storeItem("c1", "p1", item);

        // 900L is neither the snapshot (800) nor the original (1000)
        Result<CartItem> result = useCase.execute("c1", "p1", 900L);

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("price"));
    }

    @Test
    public void itemNotFound_failure() {
        // Nothing inserted in the repo

        Result<CartItem> result = useCase.execute("c1", "p-missing", 800L);

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("not found"));
    }

    @Test
    public void noConflictToResolve_failure() {
        CartItem item = clearItem("c1", "p1", 1000L);
        cartRepo.storeItem("c1", "p1", item);

        Result<CartItem> result = useCase.execute("c1", "p1", 1000L);

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("no conflict"));
    }

    @Test
    public void chosenPriceIsOriginalPrice_succeeds() {
        CartItem item = conflictedItem("c1", "p1", 800L, 1000L);
        cartRepo.storeItem("c1", "p1", item);

        // User chooses the original price
        Result<CartItem> result = useCase.execute("c1", "p1", 1000L);

        assertTrue(result.isSuccess());
        assertEquals(1000L, result.getData().unitPriceSnapshotCents);
        assertFalse(result.getData().conflictFlag);
    }

    @Test
    public void chosenPriceIsSnapshotPrice_succeeds() {
        CartItem item = conflictedItem("c1", "p1", 800L, 1000L);
        cartRepo.storeItem("c1", "p1", item);

        // User chooses the snapshot price
        Result<CartItem> result = useCase.execute("c1", "p1", 800L);

        assertTrue(result.isSuccess());
        assertEquals(800L, result.getData().unitPriceSnapshotCents);
        assertFalse(result.getData().conflictFlag);
    }

    // -----------------------------------------------------------------------
    // Stub implementation
    // -----------------------------------------------------------------------

    private static class StubCartRepository implements CartRepository {
        // Key: "cartId:productId" → CartItem
        private final Map<String, CartItem> items = new HashMap<>();
        final List<CartItem> updatedItems = new ArrayList<>();

        void storeItem(String cartId, String productId, CartItem item) {
            items.put(cartId + ":" + productId, item);
        }

        @Override
        public CartItem findCartItem(String cartId, String productId) {
            return items.get(cartId + ":" + productId);
        }

        @Override
        public void updateCartItem(CartItem item) {
            updatedItems.add(item);
            items.put(item.cartId + ":" + item.productId, item);
        }

        @Override public Cart getByIdScoped(String id, String orgId) { return null; }
        @Override public Cart findActiveCart(String customerId, String storeId, String orgId) { return null; }
        @Override public Cart findMostRecentByCreator(String createdBy, String orgId) { return null; }
        @Override public String createCart(String orgId, String customerId, String storeId, String createdBy) { return null; }
        @Override public List<CartItem> getCartItems(String cartId) { return Collections.emptyList(); }
        @Override public void insertCartItem(CartItem item) {}
        @Override public void deleteCartItem(String itemId) {}
        @Override public int getConflictCount(String cartId) { return 0; }
        @Override public void deleteCart(String cartId) {}
    }
}
