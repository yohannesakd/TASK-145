package com.roadrunner.dispatch.core.domain.repository;

import androidx.lifecycle.LiveData;
import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import java.util.List;

public interface OrderRepository {
    String createOrderFromCart(String orgId, String cartId, String customerId, String storeId, String createdBy, List<OrderItem> items);
    Order findDraftByCartId(String cartId, String orgId);
    Order getByIdScoped(String id, String orgId);
    LiveData<List<Order>> getOrders(String orgId);
    LiveData<List<Order>> getOrdersByStatus(String orgId, String status);
    LiveData<List<Order>> getOrdersByUser(String userId, String orgId);
    List<OrderItem> getOrderItems(String orderId);
    void deleteOrderItems(String orderId);
    void insertOrderItems(String orderId, List<OrderItem> items);
    void replaceOrderItems(String orderId, List<OrderItem> items);
    void updateOrder(Order order);
    void finalizeOrder(Order finalized, AuditLogEntry auditEntry);
    void applyDiscount(String orderId, String discountRuleId, long appliedAmountCents);
    void removeDiscounts(String orderId);
    List<String> getAppliedDiscountIds(String orderId);
    List<DiscountRule> getDiscountRulesByIdScoped(List<String> ids, String orgId);
    List<DiscountRule> getActiveDiscountRules(String orgId);
    ShippingTemplate getShippingTemplateScoped(String id, String orgId);
    List<ShippingTemplate> getShippingTemplates(String orgId);
    void insertShippingTemplate(ShippingTemplate template);
    void insertDiscountRule(DiscountRule rule);
}
