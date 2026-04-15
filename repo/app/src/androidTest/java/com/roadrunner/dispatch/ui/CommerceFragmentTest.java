package com.roadrunner.dispatch.ui;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;
import com.roadrunner.dispatch.presentation.commerce.cart.CartFragment;
import com.roadrunner.dispatch.presentation.commerce.catalog.CatalogFragment;
import com.roadrunner.dispatch.presentation.commerce.checkout.CheckoutFragment;
import com.roadrunner.dispatch.presentation.commerce.checkout.InvoiceFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

/**
 * UI tests for commerce fragments:
 * CatalogFragment, CartFragment, CheckoutFragment, InvoiceFragment.
 */
@RunWith(AndroidJUnit4.class)
public class CommerceFragmentTest {

    private SessionManager sessionManager;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceLocator sl = ServiceLocator.getInstance(context);
        sessionManager = sl.getSessionManager();
    }

    private void setRole(String role) {
        sessionManager.clearSession();
        sessionManager.createSession("u1", "default_org", role, "testuser");
    }

    // ── CatalogFragment ──────────────────────────────────────────────────────

    @Test
    public void catalog_workerRole_displaysProductList() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(CatalogFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.recycler_products)).check(matches(isDisplayed()));
        onView(withId(R.id.search_view)).check(matches(isDisplayed()));
    }

    @Test
    public void catalog_adminRole_displaysProductList() {
        setRole("ADMIN");
        FragmentScenario.launchInContainer(CatalogFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.recycler_products)).check(matches(isDisplayed()));
    }

    @Test
    public void catalog_complianceRole_showsAccessDenied() {
        setRole("COMPLIANCE_REVIEWER");
        FragmentScenario.launchInContainer(CatalogFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }

    // ── CartFragment ─────────────────────────────────────────────────────────

    @Test
    public void cart_workerRole_displaysCartLayout() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(CartFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.recycler_cart_items)).check(matches(isDisplayed()));
    }

    @Test
    public void cart_complianceRole_showsAccessDenied() {
        setRole("COMPLIANCE_REVIEWER");
        FragmentScenario.launchInContainer(CartFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }

    // ── CheckoutFragment ─────────────────────────────────────────────────────

    @Test
    public void checkout_adminRole_displaysCheckoutLayout() {
        setRole("ADMIN");
        Bundle args = new Bundle();
        args.putString("order_id", "nonexistent");
        FragmentScenario.launchInContainer(CheckoutFragment.class, args,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.btn_finalize)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_compute_totals)).check(matches(isDisplayed()));
    }

    @Test
    public void checkout_complianceRole_showsAccessDenied() {
        setRole("COMPLIANCE_REVIEWER");
        FragmentScenario.launchInContainer(CheckoutFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }

    // ── InvoiceFragment ──────────────────────────────────────────────────────

    @Test
    public void invoice_workerRole_displaysInvoiceLayout() {
        setRole("WORKER");
        Bundle args = new Bundle();
        args.putString("order_id", "nonexistent");
        FragmentScenario.launchInContainer(InvoiceFragment.class, args,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.text_order_id)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_done)).check(matches(isDisplayed()));
    }

    @Test
    public void invoice_complianceRole_showsAccessDenied() {
        setRole("COMPLIANCE_REVIEWER");
        FragmentScenario.launchInContainer(InvoiceFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }
}
