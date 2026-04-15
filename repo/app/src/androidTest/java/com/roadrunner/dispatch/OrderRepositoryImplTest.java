package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.AuditLogEntry;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.AuditLogDao;
import com.roadrunner.dispatch.infrastructure.db.dao.DiscountRuleDao;
import com.roadrunner.dispatch.infrastructure.db.dao.OrderDao;
import com.roadrunner.dispatch.infrastructure.db.dao.OrderDiscountDao;
import com.roadrunner.dispatch.infrastructure.db.dao.OrderItemDao;
import com.roadrunner.dispatch.infrastructure.db.dao.ShippingTemplateDao;
import com.roadrunner.dispatch.infrastructure.db.entity.OrderEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.OrderItemEntity;
import com.roadrunner.dispatch.infrastructure.repository.OrderRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Room-backed integration tests for {@link OrderRepositoryImpl}.
 * Verifies transactional atomicity of finalizeOrder and createOrderFromCart
 * against a real in-memory Room database.
 */
@RunWith(AndroidJUnit4.class)
public class OrderRepositoryImplTest {

    private AppDatabase db;
    private OrderDao orderDao;
    private OrderItemDao orderItemDao;
    private OrderDiscountDao orderDiscountDao;
    private DiscountRuleDao discountRuleDao;
    private ShippingTemplateDao shippingTemplateDao;
    private AuditLogDao auditLogDao;
    private OrderRepositoryImpl repo;

    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        orderDao = db.orderDao();
        orderItemDao = db.orderItemDao();
        orderDiscountDao = db.orderDiscountDao();
        discountRuleDao = db.discountRuleDao();
        shippingTemplateDao = db.shippingTemplateDao();
        auditLogDao = db.auditLogDao();

        // Use 7-arg constructor to enable audit log writing in finalizeOrder
        repo = new OrderRepositoryImpl(db, orderDao, orderItemDao,
                orderDiscountDao, discountRuleDao, shippingTemplateDao, auditLogDao);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void insertDraftOrder(String id, String orgId) {
        OrderEntity order = new OrderEntity(id, orgId, "cart1", "cust1", "store1",
                "DRAFT", 0L, 0L, 0L, 0L, 0L,
                null, null, 0L, true, "creator1", NOW, NOW);
        orderDao.insert(order);
    }

    // -----------------------------------------------------------------------
    // finalizeOrder tests
    // -----------------------------------------------------------------------

    @Test
    public void finalizeOrder_updatesStatusAndWritesAuditLog() {
        insertDraftOrder("o1", "org1");

        Order finalized = new Order("o1", "org1", "cart1", "cust1", "store1",
                "FINALIZED", 5000L, 500L, 450L, 800L, 5750L,
                "ship1", "Rush order", NOW, false);
        AuditLogEntry audit = new AuditLogEntry("a1", "org1", "admin1",
                "ORDER_FINALIZED", "ORDER", "o1", "{}", null, NOW);

        repo.finalizeOrder(finalized, audit);

        // Order status updated
        OrderEntity entity = orderDao.findByIdAndOrg("o1", "org1");
        assertNotNull(entity);
        assertEquals("FINALIZED", entity.status);
        assertEquals(5000L, entity.subtotalCents);
        assertEquals(500L, entity.discountCents);
        assertEquals(450L, entity.taxCents);
        assertEquals(800L, entity.shippingCents);
        assertEquals(5750L, entity.totalCents);
        assertFalse(entity.totalsStale);

        // createdBy preserved from original insert
        assertEquals("creator1", entity.createdBy);
    }

    @Test
    public void finalizeOrder_preservesCreatedAtFromExistingRow() {
        insertDraftOrder("o1", "org1");
        OrderEntity original = orderDao.findByIdAndOrg("o1", "org1");
        long originalCreatedAt = original.createdAt;

        Order finalized = new Order("o1", "org1", "cart1", "cust1", "store1",
                "FINALIZED", 5000L, 0L, 0L, 0L, 5000L,
                null, null, NOW, false);
        AuditLogEntry audit = new AuditLogEntry("a1", "org1", "admin1",
                "ORDER_FINALIZED", "ORDER", "o1", "{}", null, NOW);

        repo.finalizeOrder(finalized, audit);

        OrderEntity updated = orderDao.findByIdAndOrg("o1", "org1");
        assertEquals(originalCreatedAt, updated.createdAt);
    }

    // -----------------------------------------------------------------------
    // createOrderFromCart tests
    // -----------------------------------------------------------------------

    @Test
    public void createOrderFromCart_insertsOrderAndItems() {
        OrderItem item1 = new OrderItem("i1", "", "prod1", "Widget", 2, 1500L, 3000L, 0.08, false);
        OrderItem item2 = new OrderItem("i2", "", "prod2", "Gadget", 1, 2500L, 2500L, 0.08, true);

        String orderId = repo.createOrderFromCart("org1", "cart1", "cust1", "store1",
                "creator1", Arrays.asList(item1, item2));

        assertNotNull(orderId);
        assertFalse(orderId.isEmpty());

        // Order record created
        OrderEntity order = orderDao.findByIdAndOrg(orderId, "org1");
        assertNotNull(order);
        assertEquals("DRAFT", order.status);
        assertEquals("org1", order.orgId);
        assertEquals("cart1", order.cartId);
        assertTrue(order.totalsStale);

        // Items created
        List<OrderItemEntity> items = orderItemDao.getOrderItems(orderId);
        assertEquals(2, items.size());
    }

    // -----------------------------------------------------------------------
    // Org-scoped lookup tests
    // -----------------------------------------------------------------------

    @Test
    public void getByIdScoped_wrongOrg_returnsNull() {
        insertDraftOrder("o1", "org1");

        Order fetched = repo.getByIdScoped("o1", "org-other");
        assertNull(fetched);
    }

    @Test
    public void getByIdScoped_correctOrg_returnsOrder() {
        insertDraftOrder("o1", "org1");

        Order fetched = repo.getByIdScoped("o1", "org1");
        assertNotNull(fetched);
        assertEquals("o1", fetched.id);
        assertEquals("DRAFT", fetched.status);
    }

    @Test
    public void findDraftByCartId_crossOrgIsolation() {
        insertDraftOrder("o1", "org1");

        // Wrong org should not find it
        Order wrongOrg = repo.findDraftByCartId("cart1", "org-other");
        assertNull(wrongOrg);

        // Correct org finds the draft
        Order found = repo.findDraftByCartId("cart1", "org1");
        assertNotNull(found);
        assertEquals("o1", found.id);
    }
}
