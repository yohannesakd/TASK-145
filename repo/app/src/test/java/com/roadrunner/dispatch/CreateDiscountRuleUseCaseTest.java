package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;
import com.roadrunner.dispatch.core.domain.usecase.CreateDiscountRuleUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CreateDiscountRuleUseCaseTest {

    private StubOrderRepository orderRepo;
    private CreateDiscountRuleUseCase useCase;

    @Before
    public void setUp() {
        orderRepo = new StubOrderRepository();
        useCase = new CreateDiscountRuleUseCase(orderRepo);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private DiscountRule validRule() {
        return new DiscountRule("r1", "org1", "Summer Sale", "PERCENT_OFF", 10.0, "ACTIVE");
    }

    // -----------------------------------------------------------------------
    // Role checks
    // -----------------------------------------------------------------------

    @Test
    public void workerRole_rejected() {
        Result<DiscountRule> result = useCase.execute(validRule(), "WORKER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role")
                || result.getFirstError().toLowerCase().contains("admin")
                || result.getFirstError().toLowerCase().contains("unauthorized"));
    }

    @Test
    public void dispatcherRole_rejected() {
        Result<DiscountRule> result = useCase.execute(validRule(), "DISPATCHER");
        assertFalse(result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    public void adminRole_success() {
        DiscountRule rule = validRule();
        Result<DiscountRule> result = useCase.execute(rule, "ADMIN");
        assertTrue(result.isSuccess());
        assertEquals(1, orderRepo.insertedRules.size());
        assertSame(rule, orderRepo.insertedRules.get(0));
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    @Test
    public void blankName_failure() {
        DiscountRule rule = new DiscountRule("r1", "org1", "", "PERCENT_OFF", 10.0, "ACTIVE");
        Result<DiscountRule> result = useCase.execute(rule, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("name"));
    }

    @Test
    public void invalidType_failure() {
        DiscountRule rule = new DiscountRule("r1", "org1", "Sale", "BOGUS", 10.0, "ACTIVE");
        Result<DiscountRule> result = useCase.execute(rule, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("type"));
    }

    @Test
    public void negativeValue_failure() {
        DiscountRule rule = new DiscountRule("r1", "org1", "Sale", "PERCENT_OFF", -1.0, "ACTIVE");
        Result<DiscountRule> result = useCase.execute(rule, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("negative"));
    }

    @Test
    public void percentOver100_failure() {
        DiscountRule rule = new DiscountRule("r1", "org1", "Sale", "PERCENT_OFF", 150.0, "ACTIVE");
        Result<DiscountRule> result = useCase.execute(rule, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().contains("100"));
    }

    @Test
    public void flatOver100_noError() {
        DiscountRule rule = new DiscountRule("r1", "org1", "Flat Deal", "FLAT_OFF", 150.0, "ACTIVE");
        Result<DiscountRule> result = useCase.execute(rule, "ADMIN");
        assertTrue(result.isSuccess());
    }

    @Test
    public void invalidStatus_failure() {
        DiscountRule rule = new DiscountRule("r1", "org1", "Sale", "PERCENT_OFF", 10.0, "BOGUS");
        Result<DiscountRule> result = useCase.execute(rule, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("status"));
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubOrderRepository implements OrderRepository {
        final List<DiscountRule> insertedRules = new ArrayList<>();

        @Override public void insertDiscountRule(DiscountRule rule) { insertedRules.add(rule); }
        @Override public void insertShippingTemplate(ShippingTemplate template) {}

        @Override public String createOrderFromCart(String orgId, String cartId, String customerId,
                String storeId, String createdBy, List<OrderItem> items) { return null; }
        @Override public Order findDraftByCartId(String cartId, String orgId) { return null; }
        @Override public Order getByIdScoped(String id, String orgId) { return null; }
        @Override public LiveData<List<Order>> getOrders(String orgId) { return null; }
        @Override public LiveData<List<Order>> getOrdersByStatus(String orgId, String status) { return null; }
        @Override public LiveData<List<Order>> getOrdersByUser(String userId, String orgId) { return null; }
        @Override public List<OrderItem> getOrderItems(String orderId) { return Collections.emptyList(); }
        @Override public void deleteOrderItems(String orderId) {}
        @Override public void insertOrderItems(String orderId, List<OrderItem> items) {}
        @Override public void replaceOrderItems(String orderId, List<OrderItem> items) {}
        @Override public void updateOrder(Order order) {}
        @Override public void finalizeOrder(Order finalized, AuditLogEntry auditEntry) {}
        @Override public void applyDiscount(String orderId, String discountRuleId, long appliedAmountCents) {}
        @Override public void removeDiscounts(String orderId) {}
        @Override public List<String> getAppliedDiscountIds(String orderId) { return Collections.emptyList(); }
        @Override public List<DiscountRule> getDiscountRulesByIdScoped(List<String> ids, String orgId) { return Collections.emptyList(); }
        @Override public List<DiscountRule> getActiveDiscountRules(String orgId) { return Collections.emptyList(); }
        @Override public ShippingTemplate getShippingTemplateScoped(String id, String orgId) { return null; }
        @Override public List<ShippingTemplate> getShippingTemplates(String orgId) { return Collections.emptyList(); }
    }
}
