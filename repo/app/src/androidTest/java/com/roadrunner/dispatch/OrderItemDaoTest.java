package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.OrderItemDao;
import com.roadrunner.dispatch.infrastructure.db.entity.CartEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.OrderEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.OrderItemEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OrderItemDaoTest {

    private AppDatabase db;
    private OrderItemDao dao;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.orderItemDao();

        // Seed FK dependencies
        db.cartDao().insert(new CartEntity("cart1", "org1", "cust1", "store1", "user1", NOW, NOW));
        db.orderDao().insert(new OrderEntity("o1", "org1", "cart1", "cust1", "store1",
                "DRAFT", 0L, 0L, 0L, 0L, 0L, null, null, 0L, true, "user1", NOW, NOW));
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void insert_andGetOrderItems_returnsList() {
        dao.insert(new OrderItemEntity("i1", "o1", "p1", "Vest", 2, 2999L, 5998L, 0.08, false));

        List<OrderItemEntity> items = dao.getOrderItems("o1");
        assertEquals(1, items.size());
        assertEquals("Vest", items.get(0).productName);
        assertEquals(2, items.get(0).quantity);
    }

    @Test
    public void insertAll_addsMultipleItems() {
        List<OrderItemEntity> items = Arrays.asList(
                new OrderItemEntity("i1", "o1", "p1", "Vest", 2, 2999L, 5998L, 0.08, false),
                new OrderItemEntity("i2", "o1", "p2", "Helmet", 1, 1499L, 1499L, 0.08, false)
        );
        dao.insertAll(items);

        List<OrderItemEntity> result = dao.getOrderItems("o1");
        assertEquals(2, result.size());
    }

    @Test
    public void deleteByOrderId_removesAllItems() {
        dao.insert(new OrderItemEntity("i1", "o1", "p1", "Vest", 1, 2999L, 2999L, 0.08, false));
        dao.insert(new OrderItemEntity("i2", "o1", "p2", "Helmet", 1, 1499L, 1499L, 0.08, false));

        dao.deleteByOrderId("o1");

        List<OrderItemEntity> items = dao.getOrderItems("o1");
        assertTrue(items.isEmpty());
    }

    @Test
    public void getOrderItems_unknownOrder_returnsEmpty() {
        List<OrderItemEntity> items = dao.getOrderItems("nonexistent");
        assertTrue(items.isEmpty());
    }

    @Test
    public void insert_regulatedItem_flagPreserved() {
        dao.insert(new OrderItemEntity("i1", "o1", "p1", "Chemical Kit", 1, 4999L, 4999L, 0.08, true));

        List<OrderItemEntity> items = dao.getOrderItems("o1");
        assertTrue(items.get(0).regulated);
    }

    @Test
    public void insert_duplicateId_throws() {
        dao.insert(new OrderItemEntity("i1", "o1", "p1", "Vest", 1, 2999L, 2999L, 0.08, false));
        try {
            dao.insert(new OrderItemEntity("i1", "o1", "p2", "Helmet", 1, 1499L, 1499L, 0.08, false));
            fail("Should throw on duplicate insert");
        } catch (Exception e) {
            // Expected
        }
    }
}
