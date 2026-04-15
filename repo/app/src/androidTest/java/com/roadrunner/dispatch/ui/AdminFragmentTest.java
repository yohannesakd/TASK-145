package com.roadrunner.dispatch.ui;

import android.content.Context;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;
import com.roadrunner.dispatch.presentation.admin.AdminConfigFragment;
import com.roadrunner.dispatch.presentation.admin.AdminDashboardFragment;
import com.roadrunner.dispatch.presentation.admin.ImportExportFragment;
import com.roadrunner.dispatch.presentation.admin.OrderListFragment;
import com.roadrunner.dispatch.presentation.admin.UserManagementFragment;

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
 * UI tests for admin-area fragments:
 * AdminDashboardFragment, AdminConfigFragment, ImportExportFragment,
 * UserManagementFragment, OrderListFragment.
 */
@RunWith(AndroidJUnit4.class)
public class AdminFragmentTest {

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

    // ── AdminDashboardFragment ──────────────────────────────────────────────

    @Test
    public void adminDashboard_adminRole_displaysNavigationCards() {
        setRole("ADMIN");
        FragmentScenario.launchInContainer(AdminDashboardFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.card_products)).check(matches(isDisplayed()));
        onView(withId(R.id.card_orders)).check(matches(isDisplayed()));
        onView(withId(R.id.card_users)).check(matches(isDisplayed()));
        onView(withId(R.id.card_config)).check(matches(isDisplayed()));
        onView(withId(R.id.card_import_export)).check(matches(isDisplayed()));
    }

    @Test
    public void adminDashboard_workerRole_showsAccessDenied() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(AdminDashboardFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }

    // ── AdminConfigFragment ──────────────────────────────────────────────────

    @Test
    public void adminConfig_adminRole_displaysConfigSections() {
        setRole("ADMIN");
        FragmentScenario.launchInContainer(AdminConfigFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.btn_save_weights)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_add_product)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_add_shipping)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_add_discount_rule)).check(matches(isDisplayed()));
    }

    @Test
    public void adminConfig_workerRole_showsAccessDenied() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(AdminConfigFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.tv_config_error))
                .check(matches(withText(containsString("Access denied"))));
    }

    // ── ImportExportFragment ─────────────────────────────────────────────────

    @Test
    public void importExport_adminRole_displaysButtons() {
        setRole("ADMIN");
        FragmentScenario.launchInContainer(ImportExportFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.btn_import)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_export)).check(matches(isDisplayed()));
    }

    @Test
    public void importExport_dispatcherRole_showsAccessDenied() {
        setRole("DISPATCHER");
        FragmentScenario.launchInContainer(ImportExportFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.tv_ie_status))
                .check(matches(withText(containsString("Access denied"))));
    }

    // ── UserManagementFragment ───────────────────────────────────────────────

    @Test
    public void userManagement_adminRole_displaysUserList() {
        setRole("ADMIN");
        FragmentScenario.launchInContainer(UserManagementFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.recycler_users)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_add_user)).check(matches(isDisplayed()));
    }

    @Test
    public void userManagement_workerRole_showsAccessDenied() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(UserManagementFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.tv_empty))
                .check(matches(withText(containsString("Access denied"))));
    }

    // ── OrderListFragment ────────────────────────────────────────────────────

    @Test
    public void orderList_adminRole_displaysRecycler() {
        setRole("ADMIN");
        FragmentScenario.launchInContainer(OrderListFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.recycler_orders)).check(matches(isDisplayed()));
    }

    @Test
    public void orderList_workerRole_showsAccessDenied() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(OrderListFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }
}
