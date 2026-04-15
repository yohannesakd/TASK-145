package com.roadrunner.dispatch.ui;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.MainActivity;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

/**
 * Full UI end-to-end journey tests that launch MainActivity and drive
 * real navigation across multiple screens. Each test clears the session,
 * logs in with seeded credentials, verifies the correct dashboard loads,
 * and navigates to a sub-screen.
 *
 * These tests exercise the complete login → dashboard → detail flow
 * through the Navigation Component, proving that all fragments, ViewModels,
 * and role guards work together in a real Android runtime.
 */
@RunWith(AndroidJUnit4.class)
public class UiJourneyTest {

    private SessionManager sessionManager;

    @Before
    public void setUp() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceLocator sl = ServiceLocator.getInstance(context);
        sessionManager = sl.getSessionManager();
        sessionManager.clearSession();
        // Allow seed data callback to finish if this is a fresh install
        Thread.sleep(2000);
    }

    /**
     * Logs in by typing credentials and clicking Sign In, then waits for
     * PBKDF2 hashing (~1-3s) and navigation to complete.
     */
    private void performLogin(String username, String password) throws InterruptedException {
        onView(withId(R.id.edit_username)).perform(typeText(username), closeSoftKeyboard());
        onView(withId(R.id.edit_password)).perform(typeText(password), closeSoftKeyboard());
        onView(withId(R.id.btn_login)).perform(click());
        // PBKDF2WithHmacSHA256 (120K iterations) takes 1-3s; allow extra margin
        Thread.sleep(5000);
    }

    // ── Admin Journey ───────────────────────────────────────────────────────

    @Test
    public void adminJourney_login_dashboard_catalog() throws InterruptedException {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);

        // Login as admin
        performLogin("admin", "Admin12345678");

        // Verify Admin Dashboard loaded
        onView(withId(R.id.card_products)).check(matches(isDisplayed()));
        onView(withId(R.id.card_orders)).check(matches(isDisplayed()));
        onView(withId(R.id.card_users)).check(matches(isDisplayed()));

        // Navigate to Catalog
        onView(withId(R.id.card_products)).perform(click());
        Thread.sleep(1000);

        // Verify Catalog screen loaded
        onView(withId(R.id.recycler_products)).check(matches(isDisplayed()));

        scenario.close();
    }

    @Test
    public void adminJourney_login_dashboard_userManagement() throws InterruptedException {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);

        performLogin("admin", "Admin12345678");

        onView(withId(R.id.card_users)).check(matches(isDisplayed()));

        // Navigate to User Management
        onView(withId(R.id.card_users)).perform(click());
        Thread.sleep(1000);

        // Verify User Management screen loaded
        onView(withId(R.id.recycler_users)).check(matches(isDisplayed()));

        scenario.close();
    }

    // ── Dispatcher Journey ──────────────────────────────────────────────────

    @Test
    public void dispatcherJourney_login_dashboard_tasks() throws InterruptedException {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);

        performLogin("dispatcher", "Dispatcher1234");

        // Verify Dispatcher Dashboard loaded
        onView(withId(R.id.card_open_tasks)).check(matches(isDisplayed()));
        onView(withId(R.id.card_zones)).check(matches(isDisplayed()));

        // Navigate to Tasks
        onView(withId(R.id.card_open_tasks)).perform(click());
        Thread.sleep(1000);

        // Verify Task List loaded
        onView(withId(R.id.recycler_tasks)).check(matches(isDisplayed()));

        scenario.close();
    }

    @Test
    public void dispatcherJourney_login_dashboard_zones() throws InterruptedException {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);

        performLogin("dispatcher", "Dispatcher1234");

        onView(withId(R.id.card_zones)).check(matches(isDisplayed()));

        // Navigate to Zones
        onView(withId(R.id.card_zones)).perform(click());
        Thread.sleep(1000);

        // Verify Zone screen loaded
        onView(withId(R.id.recycler_zones)).check(matches(isDisplayed()));

        scenario.close();
    }

    // ── Worker Journey ──────────────────────────────────────────────────────

    @Test
    public void workerJourney_login_dashboard_tasks() throws InterruptedException {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);

        performLogin("worker", "Worker12345678");

        // Verify Worker Dashboard loaded
        onView(withId(R.id.card_available_tasks)).check(matches(isDisplayed()));
        onView(withId(R.id.card_my_tasks)).check(matches(isDisplayed()));

        // Wait for worker ID resolution (background thread)
        Thread.sleep(2000);

        // Navigate to Available Tasks
        onView(withId(R.id.card_available_tasks)).perform(click());
        Thread.sleep(1000);

        // Verify Task List loaded
        onView(withId(R.id.recycler_tasks)).check(matches(isDisplayed()));

        scenario.close();
    }

    // ── Compliance Reviewer Journey ─────────────────────────────────────────

    @Test
    public void complianceJourney_login_dashboard_employers() throws InterruptedException {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);

        performLogin("reviewer", "Reviewer1234");

        // Verify Compliance Dashboard loaded
        onView(withId(R.id.card_open_cases)).check(matches(isDisplayed()));
        onView(withId(R.id.card_pending_employers)).check(matches(isDisplayed()));
        onView(withId(R.id.card_reports)).check(matches(isDisplayed()));

        // Navigate to Employers
        onView(withId(R.id.card_pending_employers)).perform(click());
        Thread.sleep(1000);

        // Verify Employer List loaded
        onView(withId(R.id.recycler_employers)).check(matches(isDisplayed()));

        scenario.close();
    }

    @Test
    public void complianceJourney_login_dashboard_cases() throws InterruptedException {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);

        performLogin("reviewer", "Reviewer1234");

        onView(withId(R.id.card_open_cases)).check(matches(isDisplayed()));

        // Navigate to Cases
        onView(withId(R.id.card_open_cases)).perform(click());
        Thread.sleep(1000);

        // Verify Case List loaded
        onView(withId(R.id.recycler_cases)).check(matches(isDisplayed()));

        scenario.close();
    }

    // ── Failure Paths ───────────────────────────────────────────────────────

    @Test
    public void invalidCredentials_showsError() throws InterruptedException {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);

        performLogin("admin", "WrongPassword99");

        // Should still be on login screen with error visible
        onView(withId(R.id.text_error)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_login)).check(matches(isDisplayed()));

        scenario.close();
    }

    @Test
    public void emptyCredentials_showsError() throws InterruptedException {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);

        // Click login without entering anything
        onView(withId(R.id.btn_login)).perform(click());
        Thread.sleep(5000);

        // Should show error and remain on login screen
        onView(withId(R.id.text_error)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_login)).check(matches(isDisplayed()));

        scenario.close();
    }
}
