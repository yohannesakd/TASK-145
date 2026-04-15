package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.CartItem;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.usecase.AddToCartUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ResolveCartConflictUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.entity.ProductEntity;
import com.roadrunner.dispatch.infrastructure.repository.CartRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ProductRepositoryImpl;
import com.roadrunner.dispatch.presentation.commerce.cart.CartViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented tests for {@link CartViewModel} wired to real Room DB.
 */
@RunWith(AndroidJUnit4.class)
public class CartViewModelTest {

    private AppDatabase db;
    private CartViewModel viewModel;
    private CartRepositoryImpl cartRepo;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        cartRepo = new CartRepositoryImpl(db.cartDao(), db.cartItemDao());
        ProductRepositoryImpl productRepo = new ProductRepositoryImpl(db.productDao());

        AddToCartUseCase addToCartUseCase = new AddToCartUseCase(cartRepo, productRepo);
        ResolveCartConflictUseCase resolveUseCase = new ResolveCartConflictUseCase(cartRepo);

        viewModel = new CartViewModel(cartRepo, addToCartUseCase, resolveUseCase);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private void seedProduct() {
        db.productDao().insert(new ProductEntity("p1", "org1", "Vest", "RR",
                "Safety", "V100", "Desc", 2999L, 0.08, false, "ACTIVE", null, NOW, NOW));
    }

    @Test
    public void addToCart_validProduct_postsCartItems() throws InterruptedException {
        seedProduct();
        CountDownLatch latch = new CountDownLatch(1);
        final List<CartItem>[] observed = new List[]{null};

        viewModel.getCartItems().observeForever(items -> {
            if (items != null && !items.isEmpty()) {
                observed[0] = items;
                latch.countDown();
            }
        });

        viewModel.addToCart("cust1", "store1", "p1", 2, "org1", "user1");

        assertTrue("Cart items should load within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertEquals(1, observed[0].size());
        assertEquals(2, observed[0].get(0).quantity);
    }

    @Test
    public void addToCart_invalidProduct_postsError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        viewModel.addToCart("cust1", "store1", "nonexistent", 1, "org1", "user1");

        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void getCartItems_returnsLiveData() {
        assertNotNull(viewModel.getCartItems());
    }

    @Test
    public void getHasConflicts_returnsLiveData() {
        assertNotNull(viewModel.getHasConflicts());
    }

    @Test
    public void getLoadedCart_returnsLiveData() {
        assertNotNull(viewModel.getLoadedCart());
    }

    @Test
    public void getError_returnsLiveData() {
        assertNotNull(viewModel.getError());
    }
}
