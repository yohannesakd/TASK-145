package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.CartItem;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.usecase.AddToCartUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ComputeOrderTotalsUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CreateOrderFromCartUseCase;
import com.roadrunner.dispatch.core.domain.usecase.FinalizeCheckoutUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ScanContentUseCase;
import com.roadrunner.dispatch.core.domain.usecase.ValidateDiscountsUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.entity.ProductEntity;
import com.roadrunner.dispatch.infrastructure.db.entity.ShippingTemplateEntity;
import com.roadrunner.dispatch.infrastructure.repository.AuditLogRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.CartRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.OrderRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.ProductRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.SensitiveWordRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-end instrumented test for the commerce flow:
 * Catalog → Cart → Checkout → Invoice.
 * Uses real Room DB with all repository and use-case wiring.
 */
@RunWith(AndroidJUnit4.class)
public class CommerceFlowIntegrationTest {

    private AppDatabase db;
    private CartRepositoryImpl cartRepo;
    private OrderRepositoryImpl orderRepo;
    private AddToCartUseCase addToCartUseCase;
    private CreateOrderFromCartUseCase createOrderUseCase;
    private FinalizeCheckoutUseCase finalizeUseCase;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        cartRepo = new CartRepositoryImpl(db.cartDao(), db.cartItemDao());
        ProductRepositoryImpl productRepo = new ProductRepositoryImpl(db.productDao());
        orderRepo = new OrderRepositoryImpl(db, db.orderDao(), db.orderItemDao(),
                db.orderDiscountDao(), db.discountRuleDao(), db.shippingTemplateDao(),
                db.auditLogDao());
        AuditLogRepositoryImpl auditRepo = new AuditLogRepositoryImpl(db.auditLogDao());
        SensitiveWordRepositoryImpl sensitiveWordRepo =
                new SensitiveWordRepositoryImpl(db.sensitiveWordDao());

        addToCartUseCase = new AddToCartUseCase(cartRepo, productRepo);
        createOrderUseCase = new CreateOrderFromCartUseCase(cartRepo, orderRepo, productRepo);
        ValidateDiscountsUseCase validateDiscounts = new ValidateDiscountsUseCase();
        ComputeOrderTotalsUseCase computeTotals = new ComputeOrderTotalsUseCase();
        ScanContentUseCase scanContent = new ScanContentUseCase(sensitiveWordRepo);
        finalizeUseCase = new FinalizeCheckoutUseCase(
                orderRepo, auditRepo, validateDiscounts, computeTotals, scanContent);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private void seedProducts() {
        db.productDao().insert(new ProductEntity("prod1", "org1", "Dispatch Vest", "RR",
                "Safety", "V100", "High-vis vest", 2999L, 0.08, false, "ACTIVE", null, NOW, NOW));
        db.productDao().insert(new ProductEntity("prod2", "org1", "Safety Helmet", "RR",
                "Safety", "H200", "Hard hat", 1499L, 0.08, false, "ACTIVE", null, NOW, NOW));
    }

    private void seedShippingTemplate() {
        db.shippingTemplateDao().insert(new ShippingTemplateEntity(
                "ship1", "org1", "Standard", "Standard shipping", 599L, 3, 7, false));
    }

    // -----------------------------------------------------------------------
    // Full commerce flow
    // -----------------------------------------------------------------------

    @Test
    public void fullFlow_catalogToCartToCheckoutToInvoice() {
        seedProducts();
        seedShippingTemplate();

        // Step 1: Add to cart
        Result<CartItem> addResult = addToCartUseCase.execute(
                "cust1", "store1", "prod1", 2, "org1", "user1");
        assertTrue("Add to cart should succeed", addResult.isSuccess());
        String cartId = addResult.getData().cartId;
        assertNotNull(cartId);

        // Step 2: Add second product
        Result<CartItem> addResult2 = addToCartUseCase.execute(
                "cust1", "store1", "prod2", 1, "org1", "user1");
        assertTrue("Second add should succeed", addResult2.isSuccess());

        // Verify cart items
        List<CartItem> items = cartRepo.getCartItems(cartId);
        assertEquals(2, items.size());

        // Step 3: Create order from cart
        Result<String> orderResult = createOrderUseCase.execute(
                cartId, "user1", "ADMIN", "org1");
        assertTrue("Order creation should succeed: " + orderResult.getFirstError(),
                orderResult.isSuccess());
        String orderId = orderResult.getData();
        assertNotNull(orderId);

        // Verify draft order
        Order draft = orderRepo.getByIdScoped(orderId, "org1");
        assertNotNull(draft);
        assertEquals("DRAFT", draft.status);
        assertTrue(draft.totalsStale);

        // Verify order items
        List<OrderItem> orderItems = orderRepo.getOrderItems(orderId);
        assertEquals(2, orderItems.size());

        // Step 4: Select shipping and compute totals
        Order withShipping = new Order(orderId, "org1", cartId, "cust1", "store1",
                "DRAFT", draft.subtotalCents, draft.discountCents, draft.taxCents,
                599L, draft.totalCents, "ship1", null, 0L, true);
        orderRepo.updateOrder(withShipping);

        // Step 5: Finalize checkout
        Result<Order> finalResult = finalizeUseCase.execute(
                orderId, "user1", "ADMIN", "org1", false);
        assertTrue("Finalize should succeed: " + finalResult.getFirstError(),
                finalResult.isSuccess());

        // Step 6: Verify invoice (finalized order)
        Order finalized = orderRepo.getByIdScoped(orderId, "org1");
        assertNotNull(finalized);
        assertEquals("FINALIZED", finalized.status);
        assertFalse(finalized.totalsStale);
        assertTrue(finalized.totalCents > 0);
    }

    // -----------------------------------------------------------------------
    // Cart isolation
    // -----------------------------------------------------------------------

    @Test
    public void addToCart_differentCustomers_separateCarts() {
        seedProducts();

        Result<CartItem> r1 = addToCartUseCase.execute(
                "custA", "store1", "prod1", 1, "org1", "user1");
        Result<CartItem> r2 = addToCartUseCase.execute(
                "custB", "store1", "prod1", 1, "org1", "user1");

        assertTrue(r1.isSuccess());
        assertTrue(r2.isSuccess());
        assertNotEquals(r1.getData().cartId, r2.getData().cartId);
    }

    @Test
    public void addToCart_sameCustomer_mergesCarts() {
        seedProducts();

        Result<CartItem> r1 = addToCartUseCase.execute(
                "cust1", "store1", "prod1", 1, "org1", "user1");
        Result<CartItem> r2 = addToCartUseCase.execute(
                "cust1", "store1", "prod1", 2, "org1", "user1");

        assertTrue(r1.isSuccess());
        assertTrue(r2.isSuccess());
        assertEquals(r1.getData().cartId, r2.getData().cartId);

        List<CartItem> items = cartRepo.getCartItems(r1.getData().cartId);
        assertEquals(1, items.size());
        assertEquals(3, items.get(0).quantity);
    }

    // -----------------------------------------------------------------------
    // Order org isolation
    // -----------------------------------------------------------------------

    @Test
    public void order_crossOrgIsolation() {
        seedProducts();
        seedShippingTemplate();

        Result<CartItem> add = addToCartUseCase.execute(
                "cust1", "store1", "prod1", 1, "org1", "user1");
        assertTrue(add.isSuccess());

        Result<String> orderResult = createOrderUseCase.execute(
                add.getData().cartId, "user1", "ADMIN", "org1");
        assertTrue(orderResult.isSuccess());

        // Wrong org returns null
        assertNull(orderRepo.getByIdScoped(orderResult.getData(), "org2"));
        // Correct org returns order
        assertNotNull(orderRepo.getByIdScoped(orderResult.getData(), "org1"));
    }
}
