package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.Cart;
import com.roadrunner.dispatch.core.domain.model.CartItem;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.CartDao;
import com.roadrunner.dispatch.infrastructure.db.dao.CartItemDao;
import com.roadrunner.dispatch.infrastructure.repository.CartRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CartRepositoryImplTest {

    private AppDatabase db;
    private CartDao cartDao;
    private CartItemDao cartItemDao;
    private CartRepositoryImpl repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        cartDao = db.cartDao();
        cartItemDao = db.cartItemDao();
        repo = new CartRepositoryImpl(cartDao, cartItemDao);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -----------------------------------------------------------------------
    // Cart creation and retrieval
    // -----------------------------------------------------------------------

    @Test
    public void createCart_returnsNonNullId() {
        String cartId = repo.createCart("org1", "cust1", "store1", "user1");
        assertNotNull(cartId);
        assertFalse(cartId.isEmpty());
    }

    @Test
    public void createCart_and_getByIdScoped() {
        String cartId = repo.createCart("org1", "cust1", "store1", "user1");
        Cart cart = repo.getByIdScoped(cartId, "org1");
        assertNotNull(cart);
        assertEquals("cust1", cart.customerId);
        assertEquals("store1", cart.storeId);
        assertEquals("user1", cart.createdBy);
    }

    @Test
    public void getByIdScoped_wrongOrg_returnsNull() {
        String cartId = repo.createCart("org1", "cust1", "store1", "user1");
        assertNull(repo.getByIdScoped(cartId, "org2"));
    }

    @Test
    public void findActiveCart_matchesByCustomerStoreOrg() {
        String cartId = repo.createCart("org1", "cust1", "store1", "user1");
        Cart found = repo.findActiveCart("cust1", "store1", "org1");
        assertNotNull(found);
        assertEquals(cartId, found.id);
    }

    @Test
    public void findMostRecentByCreator_returnsLatestCart() {
        repo.createCart("org1", "cust1", "store1", "user1");
        String secondId = repo.createCart("org1", "cust2", "store2", "user1");
        Cart found = repo.findMostRecentByCreator("user1", "org1");
        assertNotNull(found);
        assertEquals(secondId, found.id);
    }

    // -----------------------------------------------------------------------
    // Cart items
    // -----------------------------------------------------------------------

    @Test
    public void insertCartItem_and_getCartItems() {
        String cartId = repo.createCart("org1", "cust1", "store1", "user1");
        CartItem item = new CartItem("item1", cartId, "prod1", 2, 1500L, false, 1500L);
        repo.insertCartItem(item);

        List<CartItem> items = repo.getCartItems(cartId);
        assertEquals(1, items.size());
        assertEquals("prod1", items.get(0).productId);
        assertEquals(2, items.get(0).quantity);
    }

    @Test
    public void findCartItem_byCartAndProduct() {
        String cartId = repo.createCart("org1", "cust1", "store1", "user1");
        repo.insertCartItem(new CartItem("item1", cartId, "prod1", 1, 1000L, false, 1000L));
        CartItem found = repo.findCartItem(cartId, "prod1");
        assertNotNull(found);
        assertEquals("item1", found.id);
    }

    @Test
    public void updateCartItem_changesQuantity() {
        String cartId = repo.createCart("org1", "cust1", "store1", "user1");
        repo.insertCartItem(new CartItem("item1", cartId, "prod1", 1, 1000L, false, 1000L));
        repo.updateCartItem(new CartItem("item1", cartId, "prod1", 5, 1000L, false, 1000L));
        List<CartItem> items = repo.getCartItems(cartId);
        assertEquals(5, items.get(0).quantity);
    }

    @Test
    public void deleteCartItem_removesItem() {
        String cartId = repo.createCart("org1", "cust1", "store1", "user1");
        repo.insertCartItem(new CartItem("item1", cartId, "prod1", 1, 1000L, false, 1000L));
        repo.deleteCartItem("item1");
        assertEquals(0, repo.getCartItems(cartId).size());
    }

    @Test
    public void getConflictCount_countsConflictedItems() {
        String cartId = repo.createCart("org1", "cust1", "store1", "user1");
        repo.insertCartItem(new CartItem("item1", cartId, "p1", 1, 1000L, true, 900L));
        repo.insertCartItem(new CartItem("item2", cartId, "p2", 1, 500L, false, 500L));
        assertEquals(1, repo.getConflictCount(cartId));
    }

    @Test
    public void deleteCart_removesCart() {
        String cartId = repo.createCart("org1", "cust1", "store1", "user1");
        repo.deleteCart(cartId);
        assertNull(repo.getByIdScoped(cartId, "org1"));
    }
}
