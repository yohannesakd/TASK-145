package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;
import com.roadrunner.dispatch.core.domain.usecase.CreateShippingTemplateUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CreateShippingTemplateUseCaseTest {

    private StubOrderRepository orderRepo;
    private CreateShippingTemplateUseCase useCase;

    @Before
    public void setUp() {
        orderRepo = new StubOrderRepository();
        useCase = new CreateShippingTemplateUseCase(orderRepo);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ShippingTemplate validTemplate() {
        return new ShippingTemplate("t1", "org1", "Standard", "Standard shipping", 500L, 2, 5, false);
    }

    // -----------------------------------------------------------------------
    // Role checks
    // -----------------------------------------------------------------------

    @Test
    public void workerRole_rejected() {
        Result<ShippingTemplate> result = useCase.execute(validTemplate(), "WORKER");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role")
                || result.getFirstError().toLowerCase().contains("admin")
                || result.getFirstError().toLowerCase().contains("unauthorized"));
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    public void adminRole_success() {
        ShippingTemplate template = validTemplate();
        Result<ShippingTemplate> result = useCase.execute(template, "ADMIN");
        assertTrue(result.isSuccess());
        assertEquals(1, orderRepo.insertedTemplates.size());
        assertSame(template, orderRepo.insertedTemplates.get(0));
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    @Test
    public void blankName_failure() {
        ShippingTemplate template = new ShippingTemplate("t1", "org1", "", "desc", 500L, 2, 5, false);
        Result<ShippingTemplate> result = useCase.execute(template, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("name"));
    }

    @Test
    public void negativeCost_failure() {
        ShippingTemplate template = new ShippingTemplate("t1", "org1", "Express", "desc", -100L, 1, 3, false);
        Result<ShippingTemplate> result = useCase.execute(template, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("cost"));
    }

    @Test
    public void negativeMinDays_failure() {
        ShippingTemplate template = new ShippingTemplate("t1", "org1", "Express", "desc", 500L, -1, 3, false);
        Result<ShippingTemplate> result = useCase.execute(template, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("minimum"));
    }

    @Test
    public void negativeMaxDays_failure() {
        ShippingTemplate template = new ShippingTemplate("t1", "org1", "Express", "desc", 500L, 0, -1, false);
        Result<ShippingTemplate> result = useCase.execute(template, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("maximum"));
    }

    @Test
    public void minExceedsMax_failure() {
        ShippingTemplate template = new ShippingTemplate("t1", "org1", "Express", "desc", 500L, 5, 3, false);
        Result<ShippingTemplate> result = useCase.execute(template, "ADMIN");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("exceed"));
    }

    @Test
    public void validPickupTemplate_success() {
        ShippingTemplate template = new ShippingTemplate("t2", "org1", "In-Store Pickup", "Pick up at store", 0L, 0, 0, true);
        Result<ShippingTemplate> result = useCase.execute(template, "ADMIN");
        assertTrue(result.isSuccess());
        assertEquals(1, orderRepo.insertedTemplates.size());
    }

    // -----------------------------------------------------------------------
    // Stub implementations
    // -----------------------------------------------------------------------

    private static class StubOrderRepository implements OrderRepository {
        final List<ShippingTemplate> insertedTemplates = new ArrayList<>();

        @Override public void insertShippingTemplate(ShippingTemplate template) { insertedTemplates.add(template); }
        @Override public void insertDiscountRule(DiscountRule rule) {}

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
