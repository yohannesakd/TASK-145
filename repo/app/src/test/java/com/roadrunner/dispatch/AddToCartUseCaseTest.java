package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.Cart;
import com.roadrunner.dispatch.core.domain.model.CartItem;
import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.repository.CartRepository;
import com.roadrunner.dispatch.core.domain.repository.ProductRepository;
import com.roadrunner.dispatch.core.domain.usecase.AddToCartUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class AddToCartUseCaseTest {

    private StubCartRepository cartRepo;
    private StubProductRepository productRepo;
    private AddToCartUseCase useCase;

    @Before
    public void setUp() {
        cartRepo = new StubCartRepository();
        productRepo = new StubProductRepository();
        useCase = new AddToCartUseCase(cartRepo, productRepo);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Product activeProduct(String id, long priceCents) {
        return new Product(id, "org1", "Product " + id, "Brand", "Series", "Model",
                "Desc", priceCents, 0.08, false, "ACTIVE", null);
    }

    private Product inactiveProduct(String id, long priceCents) {
        return new Product(id, "org1", "Product " + id, "Brand", "Series", "Model",
                "Desc", priceCents, 0.08, false, "INACTIVE", null);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void addNewProduct_newCart_createsCartAndItem() {
        productRepo.add(activeProduct("p1", 1000L));

        Result<CartItem> result = useCase.execute("cust1", "store1", "p1", 2, "org1", "user1");

        assertTrue(result.isSuccess());
        CartItem item = result.getData();
        assertNotNull(item);
        assertEquals("p1", item.productId);
        assertEquals(2, item.quantity);
        assertEquals(1000L, item.unitPriceSnapshotCents);
        assertFalse(item.conflictFlag);
        // A new cart should have been created
        assertFalse(cartRepo.carts.isEmpty());
    }

    @Test
    public void addSameProduct_samePrice_quantityIncrements() {
        productRepo.add(activeProduct("p1", 1000L));

        // First add
        useCase.execute("cust1", "store1", "p1", 1, "org1", "user1");
        // Second add
        Result<CartItem> result = useCase.execute("cust1", "store1", "p1", 3, "org1", "user1");

        assertTrue(result.isSuccess());
        CartItem item = result.getData();
        assertEquals(4, item.quantity);  // 1 + 3
        assertFalse(item.conflictFlag);
    }

    @Test
    public void addSameProduct_differentPrice_conflictFlagSet() {
        productRepo.add(activeProduct("p1", 1000L));

        // First add at $10
        useCase.execute("cust1", "store1", "p1", 1, "org1", "user1");

        // Now change price and re-add
        productRepo.add(activeProduct("p1", 1500L)); // price changed to $15

        Result<CartItem> result = useCase.execute("cust1", "store1", "p1", 1, "org1", "user1");

        assertTrue(result.isSuccess());
        CartItem item = result.getData();
        assertTrue("Conflict flag should be set on price change", item.conflictFlag);
        // snapshot price retained from original
        assertEquals(1000L, item.unitPriceSnapshotCents);
        // original price holds the new price
        assertEquals(1500L, item.originalPriceCents);
    }

    @Test
    public void addInactiveProduct_failure() {
        productRepo.add(inactiveProduct("p2", 500L));

        Result<CartItem> result = useCase.execute("cust1", "store1", "p2", 1, "org1", "user1");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("not available"));
    }

    @Test
    public void zeroQuantity_failure() {
        productRepo.add(activeProduct("p1", 1000L));

        Result<CartItem> result = useCase.execute("cust1", "store1", "p1", 0, "org1", "user1");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("quantity"));
    }

    @Test
    public void negativeQuantity_failure() {
        productRepo.add(activeProduct("p1", 1000L));

        Result<CartItem> result = useCase.execute("cust1", "store1", "p1", -1, "org1", "user1");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("quantity"));
    }

    @Test
    public void productNotFound_failure() {
        Result<CartItem> result = useCase.execute("cust1", "store1", "unknown", 1, "org1", "user1");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("not found"));
    }

    @Test
    public void crossOrgProduct_notFound() {
        // Product belongs to org2, but we request with org1
        Product p = new Product("p-cross", "org2", "Cross Org Product", "Brand", "Series", "Model",
                "Desc", 1000L, 0.08, false, "ACTIVE", null);
        productRepo.add(p);

        Result<CartItem> result = useCase.execute("cust1", "store1", "p-cross", 1, "org1", "user1");

        assertFalse("Cross-org product should not be found", result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("not found"));
    }

    @Test
    public void existingCart_reused() {
        productRepo.add(activeProduct("p1", 1000L));
        productRepo.add(activeProduct("p2", 2000L));

        useCase.execute("cust1", "store1", "p1", 1, "org1", "user1");
        int cartCountAfterFirst = cartRepo.carts.size();

        useCase.execute("cust1", "store1", "p2", 1, "org1", "user1");
        int cartCountAfterSecond = cartRepo.carts.size();

        assertEquals("Cart should be reused for same customer+store",
                cartCountAfterFirst, cartCountAfterSecond);
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubCartRepository implements CartRepository {
        final Map<String, Cart> carts = new HashMap<>();
        final Map<String, CartItem> items = new HashMap<>(); // key: cartId+":"+productId

        @Override
        public Cart findActiveCart(String customerId, String storeId, String orgId) {
            for (Cart c : carts.values()) {
                if (c.customerId.equals(customerId) && c.storeId.equals(storeId)) return c;
            }
            return null;
        }

        @Override
        public Cart findMostRecentByCreator(String createdBy, String orgId) { return null; }

        @Override
        public Cart getByIdScoped(String id, String orgId) {
            Cart c = carts.get(id);
            if (c != null && c.orgId != null && !c.orgId.equals(orgId)) return null;
            return c;
        }

        @Override
        public String createCart(String orgId, String customerId, String storeId, String createdBy) {
            String id = UUID.randomUUID().toString();
            carts.put(id, new Cart(id, orgId, customerId, storeId, createdBy));
            return id;
        }

        @Override
        public List<CartItem> getCartItems(String cartId) {
            List<CartItem> result = new ArrayList<>();
            for (Map.Entry<String, CartItem> e : items.entrySet()) {
                if (e.getKey().startsWith(cartId + ":")) result.add(e.getValue());
            }
            return result;
        }

        @Override
        public CartItem findCartItem(String cartId, String productId) {
            return items.get(cartId + ":" + productId);
        }

        @Override
        public void insertCartItem(CartItem item) {
            items.put(item.cartId + ":" + item.productId, item);
        }

        @Override
        public void updateCartItem(CartItem item) {
            items.put(item.cartId + ":" + item.productId, item);
        }

        @Override
        public void deleteCartItem(String itemId) {
            items.entrySet().removeIf(e -> e.getValue().id.equals(itemId));
        }

        @Override
        public int getConflictCount(String cartId) {
            int count = 0;
            for (CartItem i : getCartItems(cartId)) if (i.conflictFlag) count++;
            return count;
        }

        @Override
        public void deleteCart(String cartId) { carts.remove(cartId); }
    }

    private static class StubProductRepository implements ProductRepository {
        private final Map<String, Product> products = new HashMap<>();

        void add(Product p) { products.put(p.id, p); }

        @Override
        public Product getByIdScoped(String id, String orgId) {
            Product p = products.get(id);
            if (p != null && p.orgId != null && !p.orgId.equals(orgId)) return null;
            return p;
        }

        @Override
        public LiveData<List<Product>> getActiveProducts(String orgId) { return null; }

        @Override
        public List<Product> getActiveProductsSync(String orgId) { return new ArrayList<>(products.values()); }

        @Override
        public LiveData<List<Product>> searchProducts(String orgId, String query) { return null; }

        @Override
        public void insert(Product product) { products.put(product.id, product); }

        @Override
        public void update(Product product) { products.put(product.id, product); }
    }
}
