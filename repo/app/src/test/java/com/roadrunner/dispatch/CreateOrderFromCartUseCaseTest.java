package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.Cart;
import com.roadrunner.dispatch.core.domain.model.CartItem;
import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import com.roadrunner.dispatch.core.domain.repository.CartRepository;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;
import com.roadrunner.dispatch.core.domain.repository.ProductRepository;
import com.roadrunner.dispatch.core.domain.usecase.CreateOrderFromCartUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CreateOrderFromCartUseCaseTest {

    private StubCartRepository cartRepo;
    private StubOrderRepository orderRepo;
    private StubProductRepository productRepo;
    private CreateOrderFromCartUseCase useCase;

    @Before
    public void setUp() {
        cartRepo    = new StubCartRepository();
        orderRepo   = new StubOrderRepository();
        productRepo = new StubProductRepository();
        useCase = new CreateOrderFromCartUseCase(cartRepo, orderRepo, productRepo);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Cart cart(String id) {
        return new Cart(id, "org1", "cust1", "store1", "user1");
    }

    private CartItem cartItem(String cartId, String productId, int qty, long unitPriceCents) {
        return new CartItem("ci-" + productId, cartId, productId, qty, unitPriceCents, false, 0);
    }

    private Product product(String id, String name, double taxRate, boolean regulated) {
        return new Product(id, "org1", name, "brand", "s", "m",
                "desc", 1000L, taxRate, regulated, "ACTIVE", null);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void cartNotFound_failure() {
        Result<String> result = useCase.execute("no-such-cart", "user1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("Cart not found"));
    }

    @Test
    public void emptyCart_failure() {
        cartRepo.insert(cart("c1"));
        cartRepo.setItems("c1", Collections.emptyList());

        Result<String> result = useCase.execute("c1", "user1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("empty"));
    }

    @Test
    public void unresolvedConflicts_failure_includesCount() {
        cartRepo.insert(cart("c1"));
        cartRepo.setItems("c1", Collections.singletonList(cartItem("c1", "p1", 1, 1000L)));
        cartRepo.setConflictCount("c1", 2);

        Result<String> result = useCase.execute("c1", "user1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("2"));
        assertTrue(result.getFirstError().toLowerCase().contains("conflict"));
    }

    @Test
    public void successfulCreation_returnsOrderId() {
        cartRepo.insert(cart("c1"));
        cartRepo.setItems("c1", Collections.singletonList(cartItem("c1", "p1", 1, 1000L)));
        productRepo.insert(product("p1", "Widget", 0.0, false));

        Result<String> result = useCase.execute("c1", "user1", "WORKER", "org1");
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertFalse(result.getData().isEmpty());
    }

    @Test
    public void productFound_enrichesOrderItem_name_taxRate_regulated() {
        cartRepo.insert(cart("c1"));
        cartRepo.setItems("c1", Collections.singletonList(cartItem("c1", "p1", 2, 500L)));
        productRepo.insert(product("p1", "Gadget", 0.08, true));

        useCase.execute("c1", "user1", "WORKER", "org1");

        List<OrderItem> created = orderRepo.lastCreatedItems;
        assertEquals(1, created.size());
        assertEquals("Gadget", created.get(0).productName);
        assertEquals(0.08, created.get(0).taxRate, 0.0001);
        assertTrue(created.get(0).regulated);
        assertEquals(1000L, created.get(0).lineTotalCents); // 2 × 500
    }

    @Test
    public void productNotFound_usesUnknownProductFallback() {
        cartRepo.insert(cart("c1"));
        cartRepo.setItems("c1", Collections.singletonList(cartItem("c1", "p-missing", 1, 800L)));
        // Do not insert product for "p-missing"

        useCase.execute("c1", "user1", "WORKER", "org1");

        List<OrderItem> created = orderRepo.lastCreatedItems;
        assertEquals(1, created.size());
        assertEquals("Unknown Product", created.get(0).productName);
        assertEquals(0.0, created.get(0).taxRate, 0.0001);
        assertFalse(created.get(0).regulated);
    }

    @Test
    public void multipleItems_allCreatedAsOrderItems() {
        cartRepo.insert(cart("c1"));
        List<CartItem> items = Arrays.asList(
                cartItem("c1", "p1", 1, 1000L),
                cartItem("c1", "p2", 3, 200L),
                cartItem("c1", "p3", 2, 500L)
        );
        cartRepo.setItems("c1", items);
        productRepo.insert(product("p1", "A", 0.0, false));
        productRepo.insert(product("p2", "B", 0.05, false));
        productRepo.insert(product("p3", "C", 0.10, true));

        Result<String> result = useCase.execute("c1", "user1", "WORKER", "org1");
        assertTrue(result.isSuccess());
        assertEquals(3, orderRepo.lastCreatedItems.size());
    }

    @Test
    public void zeroConflicts_noConflictFailure() {
        cartRepo.insert(cart("c1"));
        cartRepo.setItems("c1", Collections.singletonList(cartItem("c1", "p1", 1, 1000L)));
        cartRepo.setConflictCount("c1", 0);
        productRepo.insert(product("p1", "Widget", 0.0, false));

        Result<String> result = useCase.execute("c1", "user1", "WORKER", "org1");
        assertTrue(result.isSuccess());
    }

    @Test
    public void complianceReviewerRole_rejected() {
        cartRepo.insert(cart("c1"));
        cartRepo.setItems("c1", Collections.singletonList(cartItem("c1", "p1", 1, 1000L)));
        productRepo.insert(product("p1", "Widget", 0.0, false));

        Result<String> result = useCase.execute("c1", "user1", "COMPLIANCE_REVIEWER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role"));
    }

    @Test
    public void crossOrgCart_notFound() {
        // Cart belongs to org1, request with org2
        cartRepo.insert(cart("c1"));
        cartRepo.setItems("c1", Collections.singletonList(cartItem("c1", "p1", 1, 1000L)));
        productRepo.insert(product("p1", "Widget", 0.0, false));

        Result<String> result = useCase.execute("c1", "user1", "WORKER", "org2");
        assertFalse("Cross-org cart should not be found", result.isSuccess());
        assertTrue(result.getFirstError().contains("Cart not found"));
    }

    @Test
    public void missingOrgId_failure() {
        cartRepo.insert(cart("c1"));
        cartRepo.setItems("c1", Collections.singletonList(cartItem("c1", "p1", 1, 1000L)));
        productRepo.insert(product("p1", "Widget", 0.0, false));

        Result<String> result = useCase.execute("c1", "user1", "WORKER", "");
        assertFalse("Empty orgId should be rejected", result.isSuccess());
    }

    @Test
    public void duplicateDraftOrder_returnsSameOrderId() {
        cartRepo.insert(cart("c1"));
        cartRepo.setItems("c1", Collections.singletonList(cartItem("c1", "p1", 1, 1000L)));
        productRepo.insert(product("p1", "Widget", 0.0, false));

        Result<String> first = useCase.execute("c1", "user1", "WORKER", "org1");
        assertTrue(first.isSuccess());

        Result<String> second = useCase.execute("c1", "user1", "WORKER", "org1");
        assertTrue(second.isSuccess());
        assertEquals("Second call should return same order ID", first.getData(), second.getData());
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubCartRepository implements CartRepository {
        private final Map<String, Cart> carts = new HashMap<>();
        private final Map<String, List<CartItem>> items = new HashMap<>();
        private final Map<String, Integer> conflictCounts = new HashMap<>();

        void insert(Cart c) { carts.put(c.id, c); }
        void setItems(String cartId, List<CartItem> cartItems) { items.put(cartId, cartItems); }
        void setConflictCount(String cartId, int count) { conflictCounts.put(cartId, count); }

        @Override public Cart getByIdScoped(String id, String orgId) {
            Cart c = carts.get(id);
            if (c != null && c.orgId != null && !c.orgId.equals(orgId)) return null;
            return c;
        }
        @Override public List<CartItem> getCartItems(String cartId) {
            return items.getOrDefault(cartId, Collections.emptyList());
        }
        @Override public int getConflictCount(String cartId) {
            return conflictCounts.getOrDefault(cartId, 0);
        }
        @Override public CartItem findCartItem(String cartId, String productId) { return null; }
        @Override public Cart findActiveCart(String customerId, String storeId, String orgId) { return null; }
        @Override public Cart findMostRecentByCreator(String createdBy, String orgId) { return null; }
        @Override public String createCart(String orgId, String customerId, String storeId, String createdBy) { return null; }
        @Override public void insertCartItem(CartItem item) {}
        @Override public void updateCartItem(CartItem item) {}
        @Override public void deleteCartItem(String itemId) {}
        @Override public void deleteCart(String cartId) {}
    }

    private static class StubOrderRepository implements OrderRepository {
        List<OrderItem> lastCreatedItems = new ArrayList<>();
        private int orderCounter = 0;
        private final Map<String, Order> draftsByCartId = new HashMap<>();

        @Override
        public String createOrderFromCart(String orgId, String cartId, String customerId,
                String storeId, String createdBy, List<OrderItem> items) {
            lastCreatedItems = new ArrayList<>(items);
            String orderId = "order-" + (++orderCounter);
            Order draft = new Order(orderId, orgId, cartId, customerId, null,
                    "DRAFT", 0L, 0L, 0L, 0L, 0L, null, null, 0L, true);
            draftsByCartId.put(cartId + "|" + orgId, draft);
            return orderId;
        }

        @Override public Order findDraftByCartId(String cartId, String orgId) {
            return draftsByCartId.get(cartId + "|" + orgId);
        }

        @Override public Order getByIdScoped(String id, String orgId) { return null; }
        @Override public List<OrderItem> getOrderItems(String orderId) { return Collections.emptyList(); }
        @Override public void deleteOrderItems(String orderId) {}
        @Override public void insertOrderItems(String orderId, List<OrderItem> items) { lastCreatedItems = new ArrayList<>(items); }
        @Override public void replaceOrderItems(String orderId, List<OrderItem> items) { lastCreatedItems = new ArrayList<>(items); }
        @Override public void updateOrder(Order order) {}
        @Override public void finalizeOrder(Order finalized, com.roadrunner.dispatch.core.domain.model.AuditLogEntry auditEntry) {}
        @Override public List<String> getAppliedDiscountIds(String orderId) { return Collections.emptyList(); }
        @Override public LiveData<List<Order>> getOrders(String orgId) { return null; }
        @Override public LiveData<List<Order>> getOrdersByStatus(String orgId, String status) { return null; }
        @Override public LiveData<List<Order>> getOrdersByUser(String userId, String orgId) { return null; }
        @Override public void applyDiscount(String orderId, String discountRuleId, long appliedAmountCents) {}
        @Override public void removeDiscounts(String orderId) {}
        @Override public List<DiscountRule> getDiscountRulesByIdScoped(List<String> ids, String orgId) { return Collections.emptyList(); }
        @Override public List<DiscountRule> getActiveDiscountRules(String orgId) { return Collections.emptyList(); }
        @Override public ShippingTemplate getShippingTemplateScoped(String id, String orgId) { return null; }
        @Override public List<ShippingTemplate> getShippingTemplates(String orgId) { return Collections.emptyList(); }
        @Override public void insertShippingTemplate(ShippingTemplate template) {}
        @Override public void insertDiscountRule(DiscountRule rule) {}
    }

    private static class StubProductRepository implements ProductRepository {
        private final Map<String, Product> products = new HashMap<>();

        @Override public Product getByIdScoped(String id, String orgId) {
            Product p = products.get(id);
            if (p != null && p.orgId != null && !p.orgId.equals(orgId)) return null;
            return p;
        }
        @Override public LiveData<List<Product>> getActiveProducts(String orgId) { return null; }
        @Override public java.util.List<Product> getActiveProductsSync(String orgId) { return new java.util.ArrayList<>(products.values()); }
        @Override public LiveData<List<Product>> searchProducts(String orgId, String query) { return null; }
        @Override public void insert(Product product) { products.put(product.id, product); }
        @Override public void update(Product product) { products.put(product.id, product); }
    }
}
