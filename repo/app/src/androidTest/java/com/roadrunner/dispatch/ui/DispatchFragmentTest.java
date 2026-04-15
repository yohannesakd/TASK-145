package com.roadrunner.dispatch.ui;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;
import com.roadrunner.dispatch.presentation.dispatch.DispatcherDashboardFragment;
import com.roadrunner.dispatch.presentation.dispatch.WorkerDashboardFragment;
import com.roadrunner.dispatch.presentation.dispatch.taskdetail.TaskDetailFragment;
import com.roadrunner.dispatch.presentation.dispatch.tasklist.TaskListFragment;
import com.roadrunner.dispatch.presentation.dispatch.zone.ZoneFragment;

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
 * UI tests for dispatch fragments:
 * DispatcherDashboardFragment, WorkerDashboardFragment,
 * TaskListFragment, TaskDetailFragment, ZoneFragment.
 */
@RunWith(AndroidJUnit4.class)
public class DispatchFragmentTest {

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

    // ── DispatcherDashboardFragment ──────────────────────────────────────────

    @Test
    public void dispatcherDashboard_dispatcherRole_displaysCards() {
        setRole("DISPATCHER");
        FragmentScenario.launchInContainer(DispatcherDashboardFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.card_open_tasks)).check(matches(isDisplayed()));
        onView(withId(R.id.card_zones)).check(matches(isDisplayed()));
    }

    @Test
    public void dispatcherDashboard_adminRole_displaysCards() {
        setRole("ADMIN");
        FragmentScenario.launchInContainer(DispatcherDashboardFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.card_open_tasks)).check(matches(isDisplayed()));
    }

    @Test
    public void dispatcherDashboard_workerRole_showsAccessDenied() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(DispatcherDashboardFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }

    // ── WorkerDashboardFragment ──────────────────────────────────────────────

    @Test
    public void workerDashboard_workerRole_displaysCards() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(WorkerDashboardFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.card_available_tasks)).check(matches(isDisplayed()));
        onView(withId(R.id.card_my_tasks)).check(matches(isDisplayed()));
    }

    @Test
    public void workerDashboard_dispatcherRole_showsAccessDenied() {
        setRole("DISPATCHER");
        FragmentScenario.launchInContainer(WorkerDashboardFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }

    // ── TaskListFragment ─────────────────────────────────────────────────────

    @Test
    public void taskList_dispatcherRole_displaysTaskList() {
        setRole("DISPATCHER");
        Bundle args = new Bundle();
        args.putString("org_id", "default_org");
        args.putBoolean("is_dispatcher", true);
        FragmentScenario.launchInContainer(TaskListFragment.class, args,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.recycler_tasks)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_create_task)).check(matches(isDisplayed()));
    }

    @Test
    public void taskList_workerRole_displaysTaskList() {
        setRole("WORKER");
        Bundle args = new Bundle();
        args.putString("org_id", "default_org");
        args.putBoolean("is_dispatcher", false);
        FragmentScenario.launchInContainer(TaskListFragment.class, args,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.recycler_tasks)).check(matches(isDisplayed()));
    }

    // ── TaskDetailFragment ───────────────────────────────────────────────────

    @Test
    public void taskDetail_dispatcherRole_displaysDetailLayout() {
        setRole("DISPATCHER");
        Bundle args = new Bundle();
        args.putString("task_id", "nonexistent");
        args.putString("org_id", "default_org");
        FragmentScenario.launchInContainer(TaskDetailFragment.class, args,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.tv_task_title)).check(matches(isDisplayed()));
    }

    // ── ZoneFragment ─────────────────────────────────────────────────────────

    @Test
    public void zone_dispatcherRole_displaysZoneList() {
        setRole("DISPATCHER");
        Bundle args = new Bundle();
        args.putString("org_id", "default_org");
        FragmentScenario.launchInContainer(ZoneFragment.class, args,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.recycler_zones)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_add_zone)).check(matches(isDisplayed()));
    }

    @Test
    public void zone_workerRole_showsAccessDenied() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(ZoneFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }
}
