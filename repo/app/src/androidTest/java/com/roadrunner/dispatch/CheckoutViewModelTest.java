package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.repository.OrderRepository;
import com.roadrunner.dispatch.core.domain.usecase.ComputeOrderTotalsUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CreateOrderFromCartUseCase;
import com.roadrunner.dispatch.core.domain.usecase.FinalizeCheckoutUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ScanContentUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ValidateDiscountsUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.entity.CartEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.CartItemEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ProductEntity;
import com.roadrunner.dispatch.infrastructure.repository.AuditLogRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.CartRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.OrderRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ProductRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.SensitiveWordRepositoryImpl;
import com.roadrunner.dispatch.presentation.commerce.checkout.CheckoutViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented integration tests for {@link CheckoutViewModel} wired to real Room DB.
 * Exercises the full create-order-from-cart → compute totals → finalize pipeline.
 */
@RunWith(AndroidJUnit4.class)
public class CheckoutViewModelTest {

    private AppDatabase db;
    private OrderRepositoryImpl orderRepo;
    private CheckoutViewModel viewModel;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        CartRepositoryImpl cartRepo = new CartRepositoryImpl(db.cartDao(), db.cartItemDao());
        ProductRepositoryImpl productRepo = new ProductRepositoryImpl(db.productDao());
        orderRepo = new OrderRepositoryImpl(db, db.orderDao(), db.orderItemDao(),
                db.orderDiscountDao(), db.discountRuleDao(), db.shippingTemplateDao(),
                db.auditLogDao());
        AuditLogRepositoryImpl auditRepo = new AuditLogRepositoryImpl(db.auditLogDao());
        SensitiveWordRepositoryImpl sensitiveWordRepo =
                new SensitiveWordRepositoryImpl(db.sensitiveWordDao());

        ValidateDiscountsUseCase validateDiscounts = new ValidateDiscountsUseCase();
        ComputeOrderTotalsUseCase computeTotals = new ComputeOrderTotalsUseCase();
        ScanContentUseCase scanContent = new ScanContentUseCase(sensitiveWordRepo);
        CreateOrderFromCartUseCase createOrder =
                new CreateOrderFromCartUseCase(cartRepo, orderRepo, productRepo);
        FinalizeCheckoutUseCase finalize = new FinalizeCheckoutUseCase(
                orderRepo, auditRepo, validateDiscounts, computeTotals, scanContent);

        viewModel = new CheckoutViewModel(
                createOrder, finalize, computeTotals, validateDiscounts, orderRepo);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private String seedCartWithProduct() {
        db.productDao().insert(new ProductEntity("prod1", "org1", "Test Product", "Brand",
                "Series", "Model", "desc", 1500L, 0.08, false, "ACTIVE", null, NOW, NOW));
        db.cartDao().insert(new CartEntity("cart1", "org1", "cust1", "store1", "user1", NOW, NOW));
        db.cartItemDao().insert(new CartItemEntity("ci1", "cart1", "prod1", 2, 1500L, false, 1500L, NOW));
        return "cart1";
    }

    // -----------------------------------------------------------------------
    // Create order from cart
    // -----------------------------------------------------------------------

    @Test
    public void createOrderFromCart_postsOrder() throws InterruptedException {
        String cartId = seedCartWithProduct();
        CountDownLatch latch = new CountDownLatch(1);
        final Order[] observed = {null};

        viewModel.getOrder().observeForever(order -> {
            if (order != null) {
                observed[0] = order;
                latch.countDown();
            }
        });

        viewModel.createOrderFromCart(cartId, "user1", "org1", "ADMIN");
        assertTrue("Order should be created within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertEquals("DRAFT", observed[0].status);
        assertTrue(observed[0].totalsStale);
    }

    @Test
    public void createOrderFromCart_postsOrderItems() throws InterruptedException {
        String cartId = seedCartWithProduct();
        CountDownLatch latch = new CountDownLatch(1);
        final int[] itemCount = {0};

        viewModel.getOrderItems().observeForever(items -> {
            if (items != null && !items.isEmpty()) {
                itemCount[0] = items.size();
                latch.countDown();
            }
        });

        viewModel.createOrderFromCart(cartId, "user1", "org1", "ADMIN");
        assertTrue("Items should load within 5s", latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, itemCount[0]);
    }

    // -----------------------------------------------------------------------
    // Error handling
    // -----------------------------------------------------------------------

    @Test
    public void createOrderFromCart_emptyCart_postsError() throws InterruptedException {
        // Cart with no items
        db.cartDao().insert(new CartEntity("cart1", "org1", "cust1", "store1", "user1", NOW, NOW));
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getError().observeForever(err -> {
            if (err != null) latch.countDown();
        });

        viewModel.createOrderFromCart("cart1", "user1", "org1", "ADMIN");
        assertTrue("Error should post within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void staleWarning_initiallyFalse() {
        assertEquals(Boolean.FALSE, viewModel.getStaleWarning().getValue());
    }
}
