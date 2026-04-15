package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.OrderTotals;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;
import com.roadrunner.dispatch.core.domain.repository.SensitiveWordRepository;
import com.roadrunner.dispatch.core.domain.usecase.ComputeOrderTotalsUseCase;
import com.roadrunner.dispatch.core.domain.usecase.FinalizeCheckoutUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ScanContentUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ValidateDiscountsUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class FinalizeCheckoutUseCaseTest {

    private StubOrderRepository orderRepo;
    private StubAuditLogRepository auditRepo;
    private ValidateDiscountsUseCase validateDiscountsUseCase;
    private ComputeOrderTotalsUseCase computeOrderTotalsUseCase;
    private FinalizeCheckoutUseCase useCase;
    private FinalizeCheckoutUseCase useCaseWithScan;

    @Before
    public void setUp() {
        orderRepo = new StubOrderRepository();
        auditRepo = new StubAuditLogRepository();
        validateDiscountsUseCase = new ValidateDiscountsUseCase();
        computeOrderTotalsUseCase = new ComputeOrderTotalsUseCase();
        useCase = new FinalizeCheckoutUseCase(orderRepo, auditRepo, validateDiscountsUseCase, computeOrderTotalsUseCase);

        // Set up a scan-enabled use case with a stub sensitive-word repo
        StubSensitiveWordRepository sensitiveWordRepo = new StubSensitiveWordRepository();
        sensitiveWordRepo.zeroTolerance.add("badword");
        sensitiveWordRepo.allWords.add("flaggedword");
        ScanContentUseCase scanUseCase = new ScanContentUseCase(sensitiveWordRepo);
        useCaseWithScan = new FinalizeCheckoutUseCase(orderRepo, auditRepo, validateDiscountsUseCase,
                computeOrderTotalsUseCase, scanUseCase);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Builds a valid DRAFT order whose stored totalCents matches what ComputeOrderTotalsUseCase
     *  would compute for a single non-regulated $10 item with no tax, free shipping. */
    private Order draftOrder(String id) {
        // subtotal = 1000, discount = 0, tax = 0, shipping = 0, total = 1000
        return new Order(id, "org1", "cart1", "cust1", "store1",
                "DRAFT",
                1000L, 0L, 0L, 0L, 1000L,
                "ship-free", null,
                System.currentTimeMillis(), false);
    }

    private Order draftOrderWithNotes(String id, String notes) {
        return new Order(id, "org1", "cart1", "cust1", "store1",
                "DRAFT",
                1000L, 0L, 0L, 0L, 1000L,
                "ship-free", notes,
                System.currentTimeMillis(), false);
    }

    private OrderItem nonRegulatedItem(String orderId) {
        return new OrderItem("item1", orderId, "prod1", "Widget", 1, 1000L, 1000L, 0.0, false);
    }

    private OrderItem regulatedItem(String orderId, String name) {
        return new OrderItem("item2", orderId, "prod2", name, 1, 1000L, 1000L, 0.0, true);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void orderNotFound_failure() {
        Result<Order> result = useCase.execute("nonexistent", "actor1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("Order not found"));
    }

    @Test
    public void orderNotDraft_failure() {
        Order order = new Order("o1", "org1", "cart1", "cust1", "store1",
                "FINALIZED", 1000L, 0L, 0L, 0L, 1000L, null, null,
                System.currentTimeMillis(), false);
        orderRepo.storeOrder(order);
        orderRepo.setItems("o1", Collections.singletonList(nonRegulatedItem("o1")));

        Result<Order> result = useCase.execute("o1", "actor1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("not in DRAFT"));
        assertTrue(result.getFirstError().contains("FINALIZED"));
    }

    @Test
    public void totalsStale_failure() {
        Order order = new Order("o1", "org1", "cart1", "cust1", "store1",
                "DRAFT", 1000L, 0L, 0L, 0L, 1000L, null, null,
                System.currentTimeMillis(), true /* totalsStale */);
        orderRepo.storeOrder(order);
        orderRepo.setItems("o1", Collections.singletonList(nonRegulatedItem("o1")));

        Result<Order> result = useCase.execute("o1", "actor1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("stale"));
    }

    @Test
    public void totalsNotComputed_failure() {
        Order order = new Order("o1", "org1", "cart1", "cust1", "store1",
                "DRAFT", 1000L, 0L, 0L, 0L, 1000L, null, null,
                0L /* totalsComputedAt=0 */, false);
        orderRepo.storeOrder(order);
        orderRepo.setItems("o1", Collections.singletonList(nonRegulatedItem("o1")));

        Result<Order> result = useCase.execute("o1", "actor1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("Totals have not been computed"));
    }

    @Test
    public void noItems_failure() {
        Order order = draftOrder("o1");
        orderRepo.storeOrder(order);
        orderRepo.setItems("o1", Collections.emptyList());

        Result<Order> result = useCase.execute("o1", "actor1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("no items"));
    }

    @Test
    public void regulatedItemWithoutNotes_failure_includesProductName() {
        Order order = draftOrder("o1");
        orderRepo.storeOrder(order);
        orderRepo.setItems("o1", Collections.singletonList(regulatedItem("o1", "AlcoBev")));

        Result<Order> result = useCase.execute("o1", "actor1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("AlcoBev"));
        assertTrue(result.getFirstError().toLowerCase().contains("regulated"));
    }

    @Test
    public void regulatedItemWithNotes_passes() {
        Order order = draftOrderWithNotes("o1", "Customer has valid ID");
        orderRepo.storeOrder(order);
        orderRepo.setItems("o1", Collections.singletonList(regulatedItem("o1", "AlcoBev")));

        Result<Order> result = useCase.execute("o1", "actor1", "WORKER", "org1");
        assertTrue(result.isSuccess());
        assertEquals("FINALIZED", result.getData().status);
    }

    @Test
    public void nonRegulatedItems_doNotRequireNotes() {
        Order order = draftOrder("o1");
        orderRepo.storeOrder(order);
        orderRepo.setItems("o1", Collections.singletonList(nonRegulatedItem("o1")));

        Result<Order> result = useCase.execute("o1", "actor1", "WORKER", "org1");
        assertTrue(result.isSuccess());
        assertEquals("FINALIZED", result.getData().status);
    }

    @Test
    public void consistencyCheckFails_discrepancyGreaterThanOneCent_failure() {
        // Store a total that is $1.00 off from what compute would produce
        Order order = new Order("o1", "org1", "cart1", "cust1", "store1",
                "DRAFT", 1000L, 0L, 0L, 0L,
                2000L /* computed would be 1000 */, "ship-free", null,
                System.currentTimeMillis(), false);
        orderRepo.storeOrder(order);
        orderRepo.setItems("o1", Collections.singletonList(nonRegulatedItem("o1")));

        Result<Order> result = useCase.execute("o1", "actor1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("consistency check failed") ||
                result.getFirstError().toLowerCase().contains("discrepancy"));
    }

    @Test
    public void successfulFinalization_orderStatusFinalized() {
        Order order = draftOrder("o1");
        orderRepo.storeOrder(order);
        orderRepo.setItems("o1", Collections.singletonList(nonRegulatedItem("o1")));

        Result<Order> result = useCase.execute("o1", "actor1", "WORKER", "org1");
        assertTrue(result.isSuccess());
        assertEquals("FINALIZED", result.getData().status);
        assertEquals("o1", result.getData().id);
    }

    @Test
    public void discountValidationFailure_propagates() {
        Order order = draftOrder("o1");
        orderRepo.storeOrder(order);
        orderRepo.setItems("o1", Collections.singletonList(nonRegulatedItem("o1")));

        // Add 4 discount IDs — ValidateDiscountsUseCase rejects more than 3
        List<DiscountRule> rules = Arrays.asList(
                new DiscountRule("d1", "org1", "D1", "PERCENT_OFF", 5.0, "ACTIVE"),
                new DiscountRule("d2", "org1", "D2", "PERCENT_OFF", 5.0, "ACTIVE"),
                new DiscountRule("d3", "org1", "D3", "PERCENT_OFF", 5.0, "ACTIVE"),
                new DiscountRule("d4", "org1", "D4", "PERCENT_OFF", 5.0, "ACTIVE")
        );
        orderRepo.setDiscounts("o1", Arrays.asList("d1", "d2", "d3", "d4"), rules);

        Result<Order> result = useCase.execute("o1", "actor1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        // At least one error about exceeding max discounts
        boolean hasDiscountError = false;
        for (String err : result.getErrors()) {
            if (err.toLowerCase().contains("discount") || err.toLowerCase().contains("maximum")) {
                hasDiscountError = true;
                break;
            }
        }
        assertTrue("Expected discount validation error", hasDiscountError);
    }

    @Test
    public void multipleRegulatedItems_allNamesInError() {
        Order order = draftOrder("o1");
        orderRepo.storeOrder(order);
        List<OrderItem> items = Arrays.asList(
                regulatedItem("o1", "AlcoBev"),
                regulatedItem("o1", "Tobacco")
        );
        orderRepo.setItems("o1", items);

        Result<Order> result = useCase.execute("o1", "actor1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        String error = result.getFirstError();
        assertTrue(error.contains("AlcoBev"));
        assertTrue(error.contains("Tobacco"));
    }

    // -----------------------------------------------------------------------
    // Role-based access control
    // -----------------------------------------------------------------------

    @Test
    public void complianceRole_rejected() {
        Result<Order> result = useCase.execute("o99", "actor1", "COMPLIANCE_REVIEWER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role"));
    }

    @Test
    public void nullRole_rejected() {
        Result<Order> result = useCase.execute("o98", "actor1", null, "org1");
        assertFalse(result.isSuccess());
    }

    @Test
    public void auditLogWrittenOnFinalization() {
        Order order = draftOrder("o1");
        orderRepo.storeOrder(order);
        orderRepo.setItems("o1", Collections.singletonList(nonRegulatedItem("o1")));

        Result<Order> result = useCase.execute("o1", "actor1", "WORKER", "org1");
        assertTrue(result.isSuccess());
        // Audit log is written transactionally via orderRepository.finalizeOrder()
        assertFalse(orderRepo.auditLogs.isEmpty());
        assertEquals("ORDER_FINALIZED", orderRepo.auditLogs.get(0).action);
    }

    @Test
    public void crossOrgOrder_notFound() {
        Order order = draftOrder("o-cross");
        orderRepo.storeOrder(order);
        orderRepo.setItems("o-cross", Collections.singletonList(nonRegulatedItem("o-cross")));

        // Order belongs to org1, request with org2
        Result<Order> result = useCase.execute("o-cross", "actor1", "WORKER", "org2");
        assertFalse("Cross-org order should not be found", result.isSuccess());
        assertTrue(result.getFirstError().contains("not found"));
    }

    @Test
    public void noShippingTemplate_failure() {
        // Create a valid DRAFT order with null shippingTemplateId
        Order order = new Order("o-nosh", "org1", "cart1", "cust1", "store1",
                "DRAFT", 1000L, 0L, 0L, 0L, 1000L,
                null, null,
                System.currentTimeMillis(), false);
        orderRepo.storeOrder(order);
        orderRepo.setItems("o-nosh", Collections.singletonList(nonRegulatedItem("o-nosh")));

        Result<Order> result = useCase.execute("o-nosh", "actor1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("shipping"));
    }

    // -----------------------------------------------------------------------
    // Content moderation tests
    // -----------------------------------------------------------------------

    @Test
    public void zeroToleranceNotes_rejected() {
        Order order = draftOrderWithNotes("o-zt", "Notes containing badword here");
        orderRepo.storeOrder(order);
        orderRepo.setItems("o-zt", Collections.singletonList(nonRegulatedItem("o-zt")));

        Result<Order> result = useCaseWithScan.execute("o-zt", "actor1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("prohibited"));
    }

    @Test
    public void flaggedNotes_rejectedByDefault() {
        Order order = draftOrderWithNotes("o-fl", "Notes containing flaggedword here");
        orderRepo.storeOrder(order);
        orderRepo.setItems("o-fl", Collections.singletonList(nonRegulatedItem("o-fl")));

        Result<Order> result = useCaseWithScan.execute("o-fl", "actor1", "WORKER", "org1");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().startsWith("CONTENT_FLAGGED:"));
    }

    @Test
    public void flaggedNotes_approvedWhenContentApproved() {
        Order order = draftOrderWithNotes("o-fa", "Notes containing flaggedword here");
        orderRepo.storeOrder(order);
        orderRepo.setItems("o-fa", Collections.singletonList(nonRegulatedItem("o-fa")));

        Result<Order> result = useCaseWithScan.execute("o-fa", "actor1", "WORKER", "org1",
                true /* contentApproved */);
        assertTrue(result.isSuccess());
        assertEquals("FINALIZED", result.getData().status);
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubSensitiveWordRepository implements SensitiveWordRepository {
        List<String> zeroTolerance = new ArrayList<>();
        List<String> allWords = new ArrayList<>();

        @Override public List<String> getZeroToleranceWords() { return zeroTolerance; }
        @Override public List<String> getAllWords() { return allWords; }
        @Override public void addWord(String word, boolean isZeroTolerance) {
            if (isZeroTolerance) zeroTolerance.add(word);
            else allWords.add(word);
        }
        @Override public void removeWord(String id) {}
    }

    private static class StubOrderRepository implements OrderRepository {
        private final Map<String, Order> orders = new HashMap<>();
        private final Map<String, List<OrderItem>> items = new HashMap<>();
        private final Map<String, List<String>> discountIds = new HashMap<>();
        private final Map<String, List<DiscountRule>> discountRules = new HashMap<>();
        public final List<Order> updatedOrders = new ArrayList<>();
        public final List<com.roadrunner.dispatch.core.domain.model.AuditLogEntry> auditLogs = new ArrayList<>();

        void storeOrder(Order order) { orders.put(order.id, order); }

        void setItems(String orderId, List<OrderItem> orderItems) {
            items.put(orderId, new ArrayList<>(orderItems));
        }

        void setDiscounts(String orderId, List<String> ids, List<DiscountRule> rules) {
            discountIds.put(orderId, ids);
            for (DiscountRule r : rules) {
                // Store by id for lookup
            }
            // Store rules under a key that getDiscountRulesById can find
            discountRules.put(orderId, rules);
        }

        @Override public Order findDraftByCartId(String cartId, String orgId) { return null; }
        @Override public Order getByIdScoped(String id, String orgId) {
            Order o = orders.get(id);
            if (o != null && o.orgId != null && !o.orgId.equals(orgId)) return null;
            return o;
        }

        @Override public List<OrderItem> getOrderItems(String orderId) {
            return items.getOrDefault(orderId, Collections.emptyList());
        }

        @Override public void deleteOrderItems(String orderId) { items.remove(orderId); }
        @Override public void insertOrderItems(String orderId, List<OrderItem> orderItems) { items.put(orderId, new ArrayList<>(orderItems)); }
        @Override public void replaceOrderItems(String orderId, List<OrderItem> orderItems) { items.put(orderId, new ArrayList<>(orderItems)); }

        @Override public void updateOrder(Order order) { updatedOrders.add(order); orders.put(order.id, order); }
        @Override public void finalizeOrder(Order finalized, com.roadrunner.dispatch.core.domain.model.AuditLogEntry auditEntry) {
            updatedOrders.add(finalized);
            orders.put(finalized.id, finalized);
            if (auditEntry != null) auditLogs.add(auditEntry);
        }

        @Override public List<String> getAppliedDiscountIds(String orderId) {
            return discountIds.getOrDefault(orderId, Collections.emptyList());
        }

        @Override public String createOrderFromCart(String orgId, String cartId, String customerId,
                String storeId, String createdBy, List<OrderItem> orderItems) { return null; }
        @Override public LiveData<List<Order>> getOrders(String orgId) { return null; }
        @Override public LiveData<List<Order>> getOrdersByStatus(String orgId, String status) { return null; }
        @Override public LiveData<List<Order>> getOrdersByUser(String userId, String orgId) { return null; }
        @Override public void applyDiscount(String orderId, String discountRuleId, long appliedAmountCents) {}
        @Override public void removeDiscounts(String orderId) {}
        @Override public List<DiscountRule> getDiscountRulesByIdScoped(List<String> ids, String orgId) {
            for (Map.Entry<String, List<String>> entry : discountIds.entrySet()) {
                if (entry.getValue().equals(ids)) {
                    return discountRules.getOrDefault(entry.getKey(), Collections.emptyList());
                }
            }
            return Collections.emptyList();
        }
        @Override public List<DiscountRule> getActiveDiscountRules(String orgId) { return Collections.emptyList(); }
        @Override public ShippingTemplate getShippingTemplateScoped(String id, String orgId) {
            if ("ship-free".equals(id)) {
                return new ShippingTemplate("ship-free", "org1", "Free Shipping", "No charge", 0L, 1, 3, false);
            }
            return null;
        }
        @Override public List<ShippingTemplate> getShippingTemplates(String orgId) { return Collections.emptyList(); }
        @Override public void insertShippingTemplate(ShippingTemplate template) {}
        @Override public void insertDiscountRule(DiscountRule rule) {}
    }

    private static class StubAuditLogRepository implements com.roadrunner.dispatch.core.domain.repository.AuditLogRepository {
        final java.util.List<com.roadrunner.dispatch.core.domain.model.AuditLogEntry> logs = new java.util.ArrayList<>();
        @Override public void log(com.roadrunner.dispatch.core.domain.model.AuditLogEntry e) { logs.add(e); }
        @Override public androidx.lifecycle.LiveData<java.util.List<com.roadrunner.dispatch.core.domain.model.AuditLogEntry>> getLogsForCase(String c, String o) { return null; }
        @Override public androidx.lifecycle.LiveData<java.util.List<com.roadrunner.dispatch.core.domain.model.AuditLogEntry>> getAllLogs(String o) { return null; }
    }
}
