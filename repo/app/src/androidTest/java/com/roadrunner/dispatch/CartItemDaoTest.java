package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.CartItemDao;
import com.roadrunner.dispatch.infrastructure.db.entity.CartEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.CartItemEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ProductEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CartItemDaoTest {

    private AppDatabase db;
    private CartItemDao dao;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.cartItemDao();

        // Seed FK dependencies
        db.productDao().insert(new ProductEntity("p1", "org1", "Vest", "RR", "Safety", "V100",
                "Desc", 2999L, 0.08, false, "ACTIVE", null, NOW, NOW));
        db.productDao().insert(new ProductEntity("p2", "org1", "Helmet", "RR", "Safety", "H200",
                "Desc", 1499L, 0.08, false, "ACTIVE", null, NOW, NOW));
        db.cartDao().insert(new CartEntity("cart1", "org1", "cust1", "store1", "user1", NOW, NOW));
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void insert_andGetCartItems_returnsList() {
        dao.insert(new CartItemEntity("ci1", "cart1", "p1", 2, 2999L, false, 0L, NOW));

        List<CartItemEntity> items = dao.getCartItems("cart1");
        assertEquals(1, items.size());
        assertEquals(2, items.get(0).quantity);
    }

    @Test
    public void findByCartAndProduct_returnsMatch() {
        dao.insert(new CartItemEntity("ci1", "cart1", "p1", 1, 2999L, false, 0L, NOW));

        CartItemEntity found = dao.findByCartAndProduct("cart1", "p1");
        assertNotNull(found);
        assertEquals("ci1", found.id);
    }

    @Test
    public void findByCartAndProduct_noMatch_returnsNull() {
        assertNull(dao.findByCartAndProduct("cart1", "nonexistent"));
    }

    @Test
    public void update_modifiesItem() {
        CartItemEntity item = new CartItemEntity("ci1", "cart1", "p1", 1, 2999L, false, 0L, NOW);
        dao.insert(item);

        item.quantity = 5;
        dao.update(item);

        CartItemEntity found = dao.findByCartAndProduct("cart1", "p1");
        assertEquals(5, found.quantity);
    }

    @Test
    public void deleteById_removesItem() {
        dao.insert(new CartItemEntity("ci1", "cart1", "p1", 1, 2999L, false, 0L, NOW));
        dao.insert(new CartItemEntity("ci2", "cart1", "p2", 1, 1499L, false, 0L, NOW));

        dao.deleteById("ci1");

        List<CartItemEntity> items = dao.getCartItems("cart1");
        assertEquals(1, items.size());
        assertEquals("ci2", items.get(0).id);
    }

    @Test
    public void countConflicts_noConflicts_returnsZero() {
        dao.insert(new CartItemEntity("ci1", "cart1", "p1", 1, 2999L, false, 0L, NOW));

        assertEquals(0, dao.countConflicts("cart1"));
    }

    @Test
    public void countConflicts_withConflicts_returnsCount() {
        dao.insert(new CartItemEntity("ci1", "cart1", "p1", 1, 2999L, true, 2500L, NOW));
        dao.insert(new CartItemEntity("ci2", "cart1", "p2", 1, 1499L, false, 0L, NOW));

        assertEquals(1, dao.countConflicts("cart1"));
    }

    @Test
    public void getCartItems_emptyCart_returnsEmpty() {
        List<CartItemEntity> items = dao.getCartItems("cart1");
        assertTrue(items.isEmpty());
    }

    @Test
    public void insert_duplicateId_throws() {
        dao.insert(new CartItemEntity("ci1", "cart1", "p1", 1, 2999L, false, 0L, NOW));
        try {
            dao.insert(new CartItemEntity("ci1", "cart1", "p2", 1, 1499L, false, 0L, NOW));
            fail("Should throw on duplicate insert");
        } catch (Exception e) {
            // Expected — ABORT strategy
        }
    }
}
