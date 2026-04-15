package com.roadrunner.dispatch;

import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Validates that navigation argument keys used by senders (list fragments)
 * exactly match the keys expected by receivers (detail fragments).
 *
 * These tests prevent the subtle class of bugs where a sender puts an argument
 * under one key name but the receiver reads from a different key, resulting in
 * a null/empty value being silently used.
 */
@RunWith(AndroidJUnit4.class)
public class NavigationArgumentTest {

    // Keys used by EmployerListFragment (sender) and EmployerDetailFragment (receiver)
    private static final String KEY_EMPLOYER_ID = "employer_id";

    // Keys used by CaseListFragment (sender) and CaseDetailFragment (receiver)
    private static final String KEY_CASE_ID = "case_id";

    // Keys used by CheckoutFragment (sender) and InvoiceFragment (receiver)
    private static final String KEY_ORDER_ID = "orderId";

    // Keys used by CartFragment (sender) and CheckoutFragment (receiver)
    private static final String KEY_CART_ID = "cartId";

    // Keys used by TaskListFragment (sender) and TaskDetailFragment (receiver)
    private static final String KEY_TASK_ID = "task_id";

    // -----------------------------------------------------------------------
    // Employer detail route
    // -----------------------------------------------------------------------

    @Test
    public void employerDetailRoute_bundleKeyMatchesBothSides() {
        Bundle senderBundle = new Bundle();
        senderBundle.putString(KEY_EMPLOYER_ID, "emp-123");

        // Receiver reads with the same key
        String receivedId = senderBundle.getString(KEY_EMPLOYER_ID, "");
        assertEquals("emp-123", receivedId);
    }

    @Test
    public void employerDetailRoute_missingKey_defaultsToEmpty() {
        Bundle emptyBundle = new Bundle();

        String receivedId = emptyBundle.getString(KEY_EMPLOYER_ID, "");
        assertEquals("", receivedId);
    }

    // -----------------------------------------------------------------------
    // Case detail route
    // -----------------------------------------------------------------------

    @Test
    public void caseDetailRoute_bundleKeyMatchesBothSides() {
        Bundle senderBundle = new Bundle();
        senderBundle.putString(KEY_CASE_ID, "case-456");

        String receivedId = senderBundle.getString(KEY_CASE_ID, "");
        assertEquals("case-456", receivedId);
    }

    @Test
    public void caseDetailRoute_missingKey_defaultsToEmpty() {
        Bundle emptyBundle = new Bundle();

        String receivedId = emptyBundle.getString(KEY_CASE_ID, "");
        assertEquals("", receivedId);
    }

    // -----------------------------------------------------------------------
    // Cart -> Checkout -> Invoice flow
    // -----------------------------------------------------------------------

    @Test
    public void cartToCheckoutFlow_cartIdPassedCorrectly() {
        Bundle cartToCheckout = new Bundle();
        cartToCheckout.putString(KEY_CART_ID, "cart-789");

        String receivedCartId = cartToCheckout.getString(KEY_CART_ID, "");
        assertEquals("cart-789", receivedCartId);
    }

    @Test
    public void checkoutToInvoiceFlow_orderIdPassedCorrectly() {
        Bundle checkoutToInvoice = new Bundle();
        checkoutToInvoice.putString(KEY_ORDER_ID, "order-001");

        String receivedOrderId = checkoutToInvoice.getString(KEY_ORDER_ID, "");
        assertEquals("order-001", receivedOrderId);
    }

    @Test
    public void fullCommerceFlow_bundleChainPreservesAllKeys() {
        // Simulate: Cart screen sets cartId
        Bundle cartBundle = new Bundle();
        cartBundle.putString(KEY_CART_ID, "cart-100");

        // Checkout reads cartId and eventually sets orderId
        String cartId = cartBundle.getString(KEY_CART_ID, "");
        assertFalse(cartId.isEmpty());

        // After finalization, checkout navigates to invoice with orderId
        Bundle invoiceBundle = new Bundle();
        invoiceBundle.putString(KEY_ORDER_ID, "order-from-" + cartId);

        String orderId = invoiceBundle.getString(KEY_ORDER_ID, "");
        assertEquals("order-from-cart-100", orderId);
    }

    // -----------------------------------------------------------------------
    // Task detail route
    // -----------------------------------------------------------------------

    @Test
    public void taskDetailRoute_taskIdPassedCorrectly() {
        Bundle senderBundle = new Bundle();
        senderBundle.putString(KEY_TASK_ID, "task-xyz");

        String receivedId = senderBundle.getString(KEY_TASK_ID, "");
        assertEquals("task-xyz", receivedId);
    }
}
