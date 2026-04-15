package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.OrderDiscountDao;
import com.roadrunner.dispatch.infrastructure.db.entity.CartEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.DiscountRuleEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.OrderDiscountEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.OrderEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OrderDiscountDaoTest {

    private AppDatabase db;
    private OrderDiscountDao dao;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.orderDiscountDao();

        // Seed FK dependencies: cart → order, discount rule
        db.cartDao().insert(new CartEntity("cart1", "org1", "cust1", "store1", "user1", NOW, NOW));
        db.orderDao().insert(new OrderEntity("o1", "org1", "cart1", "cust1", "store1",
                "DRAFT", 5000L, 0L, 0L, 0L, 5000L, null, null, 0L, true, "user1", NOW, NOW));
        db.discountRuleDao().insert(new DiscountRuleEntity("dr1", "org1", "10%", "PERCENT_OFF", 10.0, "ACTIVE", NOW));
        db.discountRuleDao().insert(new DiscountRuleEntity("dr2", "org1", "$5", "FLAT_OFF", 500.0, "ACTIVE", NOW));
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void insert_andGetDiscountsForOrder_returnsList() {
        dao.insert(new OrderDiscountEntity("o1", "dr1", 500L, NOW));

        List<OrderDiscountEntity> discounts = dao.getDiscountsForOrder("o1");
        assertEquals(1, discounts.size());
        assertEquals(500L, discounts.get(0).appliedAmountCents);
    }

    @Test
    public void multipleDiscounts_forSameOrder() {
        dao.insert(new OrderDiscountEntity("o1", "dr1", 500L, NOW));
        dao.insert(new OrderDiscountEntity("o1", "dr2", 300L, NOW));

        List<OrderDiscountEntity> discounts = dao.getDiscountsForOrder("o1");
        assertEquals(2, discounts.size());
    }

    @Test
    public void deleteByOrderId_removesAll() {
        dao.insert(new OrderDiscountEntity("o1", "dr1", 500L, NOW));
        dao.insert(new OrderDiscountEntity("o1", "dr2", 300L, NOW));

        dao.deleteByOrderId("o1");

        List<OrderDiscountEntity> discounts = dao.getDiscountsForOrder("o1");
        assertTrue(discounts.isEmpty());
    }

    @Test
    public void getDiscountsForOrder_unknownOrder_returnsEmpty() {
        List<OrderDiscountEntity> discounts = dao.getDiscountsForOrder("nonexistent");
        assertTrue(discounts.isEmpty());
    }

    @Test
    public void insert_duplicateCompositeKey_throws() {
        dao.insert(new OrderDiscountEntity("o1", "dr1", 500L, NOW));
        try {
            dao.insert(new OrderDiscountEntity("o1", "dr1", 600L, NOW));
            fail("Should throw on duplicate composite key");
        } catch (Exception e) {
            // Expected — ABORT strategy
        }
    }
}
