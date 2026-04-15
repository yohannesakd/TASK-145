package com.roadrunner.dispatch.infrastructure.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.AuditLogDao;
import com.roadrunner.dispatch.infrastructure.db.dao.DiscountRuleDao;
import com.roadrunner.dispatch.infrastructure.db.dao.OrderDao;
import com.roadrunner.dispatch.infrastructure.db.dao.OrderDiscountDao;
import com.roadrunner.dispatch.infrastructure.db.dao.OrderItemDao;
import com.roadrunner.dispatch.infrastructure.db.dao.ShippingTemplateDao;
import com.roadrunner.dispatch.infrastructure.db.entity.AuditLogEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.DiscountRuleEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.OrderDiscountEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.OrderEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.OrderItemEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ShippingTemplateEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderRepositoryImpl implements OrderRepository {

    private final AppDatabase db;
    private final OrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final OrderDiscountDao orderDiscountDao;
    private final DiscountRuleDao discountRuleDao;
    private final ShippingTemplateDao shippingTemplateDao;
    private final AuditLogDao auditLogDao;

    public OrderRepositoryImpl(
            AppDatabase db,
            OrderDao orderDao,
            OrderItemDao orderItemDao,
            OrderDiscountDao orderDiscountDao,
            DiscountRuleDao discountRuleDao,
            ShippingTemplateDao shippingTemplateDao) {
        this(db, orderDao, orderItemDao, orderDiscountDao, discountRuleDao, shippingTemplateDao, null);
    }

    public OrderRepositoryImpl(
            AppDatabase db,
            OrderDao orderDao,
            OrderItemDao orderItemDao,
            OrderDiscountDao orderDiscountDao,
            DiscountRuleDao discountRuleDao,
            ShippingTemplateDao shippingTemplateDao,
            AuditLogDao auditLogDao) {
        this.db = db;
        this.orderDao = orderDao;
        this.orderItemDao = orderItemDao;
        this.orderDiscountDao = orderDiscountDao;
        this.discountRuleDao = discountRuleDao;
        this.shippingTemplateDao = shippingTemplateDao;
        this.auditLogDao = auditLogDao;
    }

    /**
     * Creates a DRAFT order and inserts all order items sequentially.
     * Returns the new order ID.
     */
    @Override
    public String createOrderFromCart(String orgId, String cartId, String customerId,
                                       String storeId, String createdBy, List<OrderItem> items) {
        long now = System.currentTimeMillis();
        String orderId = UUID.randomUUID().toString();

        OrderEntity orderEntity = new OrderEntity(
                orderId,
                orgId,
                cartId,
                customerId,
                storeId,
                "DRAFT",
                0L,  // subtotalCents — not yet computed
                0L,  // discountCents
                0L,  // taxCents
                0L,  // shippingCents
                0L,  // totalCents
                null, // shippingTemplateId
                null, // orderNotes
                0L,  // totalsComputedAt — 0 means not yet computed
                true, // totalsStale — true until explicitly computed
                createdBy,
                now,
                now
        );
        List<OrderItemEntity> itemEntities = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            String itemId = item.id != null ? item.id : UUID.randomUUID().toString();
            itemEntities.add(new OrderItemEntity(
                    itemId,
                    orderId,
                    item.productId,
                    item.productName,
                    item.quantity,
                    item.unitPriceCents,
                    item.lineTotalCents,
                    item.taxRate,
                    item.regulated
            ));
        }

        final OrderEntity finalOrderEntity = orderEntity;
        final List<OrderItemEntity> finalItemEntities = itemEntities;
        db.runInTransaction(() -> {
            orderDao.insert(finalOrderEntity);
            orderItemDao.insertAll(finalItemEntities);
        });

        return orderId;
    }

    @Override
    public Order findDraftByCartId(String cartId, String orgId) {
        OrderEntity e = orderDao.findDraftByCartIdAndOrg(cartId, orgId);
        return e == null ? null : toDomain(e);
    }

    @Override
    public Order getByIdScoped(String id, String orgId) {
        OrderEntity e = orderDao.findByIdAndOrg(id, orgId);
        return e == null ? null : toDomain(e);
    }

    @Override
    public LiveData<List<Order>> getOrders(String orgId) {
        return Transformations.map(orderDao.getOrders(orgId), entities -> {
            List<Order> orders = new ArrayList<>();
            if (entities != null) {
                for (OrderEntity e : entities) orders.add(toDomain(e));
            }
            return orders;
        });
    }

    @Override
    public LiveData<List<Order>> getOrdersByStatus(String orgId, String status) {
        return Transformations.map(orderDao.getOrdersByStatus(orgId, status), entities -> {
            List<Order> orders = new ArrayList<>();
            if (entities != null) {
                for (OrderEntity e : entities) orders.add(toDomain(e));
            }
            return orders;
        });
    }

    @Override
    public LiveData<List<Order>> getOrdersByUser(String userId, String orgId) {
        return Transformations.map(orderDao.getOrdersByUserAndOrg(userId, orgId), entities -> {
            List<Order> orders = new ArrayList<>();
            if (entities != null) {
                for (OrderEntity e : entities) orders.add(toDomain(e));
            }
            return orders;
        });
    }

    @Override
    public List<OrderItem> getOrderItems(String orderId) {
        List<OrderItemEntity> entities = orderItemDao.getOrderItems(orderId);
        List<OrderItem> items = new ArrayList<>(entities.size());
        for (OrderItemEntity e : entities) items.add(toOrderItemDomain(e));
        return items;
    }

    @Override
    public void deleteOrderItems(String orderId) {
        orderItemDao.deleteByOrderId(orderId);
    }

    @Override
    public void insertOrderItems(String orderId, List<OrderItem> items) {
        List<OrderItemEntity> entities = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            String itemId = item.id != null ? item.id : UUID.randomUUID().toString();
            entities.add(new OrderItemEntity(
                    itemId,
                    orderId,
                    item.productId,
                    item.productName,
                    item.quantity,
                    item.unitPriceCents,
                    item.lineTotalCents,
                    item.taxRate,
                    item.regulated
            ));
        }
        db.runInTransaction(() -> orderItemDao.insertAll(entities));
    }

    @Override
    public void replaceOrderItems(String orderId, List<OrderItem> items) {
        List<OrderItemEntity> entities = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            String itemId = item.id != null ? item.id : UUID.randomUUID().toString();
            entities.add(new OrderItemEntity(
                    itemId,
                    orderId,
                    item.productId,
                    item.productName,
                    item.quantity,
                    item.unitPriceCents,
                    item.lineTotalCents,
                    item.taxRate,
                    item.regulated
            ));
        }
        db.runInTransaction(() -> {
            orderItemDao.deleteByOrderId(orderId);
            orderItemDao.insertAll(entities);
        });
    }

    @Override
    public void updateOrder(Order order) {
        OrderEntity existing = orderDao.findByIdAndOrg(order.id, order.orgId);
        long createdAt = existing != null ? existing.createdAt : System.currentTimeMillis();
        String createdBy = existing != null ? existing.createdBy : "";

        OrderEntity e = new OrderEntity(
                order.id,
                order.orgId,
                order.cartId,
                order.customerId,
                order.storeId,
                order.status,
                order.subtotalCents,
                order.discountCents,
                order.taxCents,
                order.shippingCents,
                order.totalCents,
                order.shippingTemplateId,
                order.orderNotes,
                order.totalsComputedAt,
                order.totalsStale,
                createdBy,
                createdAt,
                System.currentTimeMillis()
        );
        orderDao.update(e);
    }

    @Override
    public void finalizeOrder(Order finalized, AuditLogEntry auditEntry) {
        OrderEntity existing = orderDao.findByIdAndOrg(finalized.id, finalized.orgId);
        long createdAt = existing != null ? existing.createdAt : System.currentTimeMillis();
        String createdBy = existing != null ? existing.createdBy : "";

        OrderEntity e = new OrderEntity(
                finalized.id,
                finalized.orgId,
                finalized.cartId,
                finalized.customerId,
                finalized.storeId,
                finalized.status,
                finalized.subtotalCents,
                finalized.discountCents,
                finalized.taxCents,
                finalized.shippingCents,
                finalized.totalCents,
                finalized.shippingTemplateId,
                finalized.orderNotes,
                finalized.totalsComputedAt,
                finalized.totalsStale,
                createdBy,
                createdAt,
                System.currentTimeMillis()
        );

        AuditLogEntity logEntity = new AuditLogEntity(
                auditEntry.id,
                auditEntry.orgId,
                auditEntry.actorId,
                auditEntry.action,
                auditEntry.targetType,
                auditEntry.targetId,
                auditEntry.details != null ? auditEntry.details : "{}",
                auditEntry.caseId,
                auditEntry.createdAt
        );

        db.runInTransaction(() -> {
            orderDao.update(e);
            if (auditLogDao != null) {
                auditLogDao.insert(logEntity);
            }
        });
    }

    @Override
    public void applyDiscount(String orderId, String discountRuleId, long appliedAmountCents) {
        OrderDiscountEntity e = new OrderDiscountEntity(
                orderId,
                discountRuleId,
                appliedAmountCents,
                System.currentTimeMillis()
        );
        orderDiscountDao.insert(e);
    }

    @Override
    public void removeDiscounts(String orderId) {
        orderDiscountDao.deleteByOrderId(orderId);
    }

    @Override
    public List<String> getAppliedDiscountIds(String orderId) {
        List<OrderDiscountEntity> entities = orderDiscountDao.getDiscountsForOrder(orderId);
        List<String> ids = new ArrayList<>(entities.size());
        for (OrderDiscountEntity e : entities) ids.add(e.discountRuleId);
        return ids;
    }

    @Override
    public List<DiscountRule> getDiscountRulesByIdScoped(List<String> ids, String orgId) {
        List<DiscountRuleEntity> entities = discountRuleDao.findByIdsAndOrg(ids, orgId);
        List<DiscountRule> rules = new ArrayList<>(entities.size());
        for (DiscountRuleEntity e : entities) rules.add(toDiscountRuleDomain(e));
        return rules;
    }

    /**
     * Returns active discount rules for the given org.
     * Because Room's DiscountRuleDao exposes active rules only via LiveData, this
     * method reads the current cached value synchronously. Callers must ensure the
     * LiveData has been observed before calling this on a background thread.
     * If the value is not yet available, an empty list is returned.
     */
    @Override
    public List<DiscountRule> getActiveDiscountRules(String orgId) {
        List<DiscountRuleEntity> entities = discountRuleDao.getActiveRulesSync(orgId);
        if (entities == null) return new ArrayList<>();
        List<DiscountRule> rules = new ArrayList<>(entities.size());
        for (DiscountRuleEntity e : entities) rules.add(toDiscountRuleDomain(e));
        return rules;
    }

    @Override
    public ShippingTemplate getShippingTemplateScoped(String id, String orgId) {
        ShippingTemplateEntity e = shippingTemplateDao.findByIdAndOrg(id, orgId);
        return e == null ? null : toShippingTemplateDomain(e);
    }

    @Override
    public List<ShippingTemplate> getShippingTemplates(String orgId) {
        List<ShippingTemplateEntity> entities = shippingTemplateDao.getTemplates(orgId);
        List<ShippingTemplate> templates = new ArrayList<>(entities.size());
        for (ShippingTemplateEntity e : entities) templates.add(toShippingTemplateDomain(e));
        return templates;
    }

    @Override
    public void insertShippingTemplate(ShippingTemplate template) {
        ShippingTemplateEntity entity = new ShippingTemplateEntity(
                template.id,
                template.orgId,
                template.name,
                template.description,
                template.costCents,
                template.minDays,
                template.maxDays,
                template.isPickup
        );
        shippingTemplateDao.insert(entity);
    }

    @Override
    public void insertDiscountRule(DiscountRule rule) {
        DiscountRuleEntity entity = new DiscountRuleEntity(
                rule.id,
                rule.orgId,
                rule.name,
                rule.type,
                rule.value,
                rule.status,
                System.currentTimeMillis()
        );
        discountRuleDao.insert(entity);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private Order toDomain(OrderEntity e) {
        return new Order(
                e.id,
                e.orgId,
                e.cartId,
                e.customerId,
                e.storeId,
                e.status,
                e.subtotalCents,
                e.discountCents,
                e.taxCents,
                e.shippingCents,
                e.totalCents,
                e.shippingTemplateId,
                e.orderNotes,
                e.totalsComputedAt,
                e.totalsStale
        );
    }

    private OrderItem toOrderItemDomain(OrderItemEntity e) {
        return new OrderItem(
                e.id,
                e.orderId,
                e.productId,
                e.productName,
                e.quantity,
                e.unitPriceCents,
                e.lineTotalCents,
                e.taxRate,
                e.regulated
        );
    }

    private DiscountRule toDiscountRuleDomain(DiscountRuleEntity e) {
        return new DiscountRule(e.id, e.orgId, e.name, e.type, e.value, e.status);
    }

    private ShippingTemplate toShippingTemplateDomain(ShippingTemplateEntity e) {
        return new ShippingTemplate(
                e.id,
                e.orgId,
                e.name,
                e.description,
                e.costCents,
                e.minDays,
                e.maxDays,
                e.isPickup
        );
    }
}
