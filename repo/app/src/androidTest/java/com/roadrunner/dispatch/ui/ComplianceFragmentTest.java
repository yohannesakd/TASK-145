package com.roadrunner.dispatch.ui;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;
import com.roadrunner.dispatch.presentation.compliance.ComplianceDashboardFragment;
import com.roadrunner.dispatch.presentation.compliance.cases.CaseDetailFragment;
import com.roadrunner.dispatch.presentation.compliance.cases.CaseListFragment;
import com.roadrunner.dispatch.presentation.compliance.employer.EmployerDetailFragment;
import com.roadrunner.dispatch.presentation.compliance.employer.EmployerListFragment;
import com.roadrunner.dispatch.presentation.compliance.reports.ReportFragment;

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
 * UI tests for compliance fragments:
 * ComplianceDashboardFragment, EmployerListFragment, EmployerDetailFragment,
 * CaseListFragment, CaseDetailFragment, ReportFragment.
 */
@RunWith(AndroidJUnit4.class)
public class ComplianceFragmentTest {

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

    // ── ComplianceDashboardFragment ──────────────────────────────────────────

    @Test
    public void complianceDashboard_reviewerRole_displaysCards() {
        setRole("COMPLIANCE_REVIEWER");
        FragmentScenario.launchInContainer(ComplianceDashboardFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.card_open_cases)).check(matches(isDisplayed()));
        onView(withId(R.id.card_pending_employers)).check(matches(isDisplayed()));
        onView(withId(R.id.card_reports)).check(matches(isDisplayed()));
    }

    @Test
    public void complianceDashboard_adminRole_displaysCards() {
        setRole("ADMIN");
        FragmentScenario.launchInContainer(ComplianceDashboardFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.card_pending_employers)).check(matches(isDisplayed()));
    }

    @Test
    public void complianceDashboard_workerRole_showsAccessDenied() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(ComplianceDashboardFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }

    // ── EmployerListFragment ─────────────────────────────────────────────────

    @Test
    public void employerList_reviewerRole_displaysEmployerList() {
        setRole("COMPLIANCE_REVIEWER");
        FragmentScenario.launchInContainer(EmployerListFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.recycler_employers)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_add_employer)).check(matches(isDisplayed()));
    }

    @Test
    public void employerList_workerRole_showsAccessDenied() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(EmployerListFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }

    // ── EmployerDetailFragment ───────────────────────────────────────────────

    @Test
    public void employerDetail_reviewerRole_displaysForm() {
        setRole("COMPLIANCE_REVIEWER");
        Bundle args = new Bundle();
        args.putString("employer_id", "nonexistent");
        FragmentScenario.launchInContainer(EmployerDetailFragment.class, args,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.et_legal_name)).check(matches(isDisplayed()));
        onView(withId(R.id.et_ein)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_verify_save)).check(matches(isDisplayed()));
    }

    @Test
    public void employerDetail_dispatcherRole_showsAccessDenied() {
        setRole("DISPATCHER");
        FragmentScenario.launchInContainer(EmployerDetailFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }

    // ── CaseListFragment ─────────────────────────────────────────────────────

    @Test
    public void caseList_reviewerRole_displaysCaseList() {
        setRole("COMPLIANCE_REVIEWER");
        FragmentScenario.launchInContainer(CaseListFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.recycler_cases)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_open_case)).check(matches(isDisplayed()));
    }

    @Test
    public void caseList_adminRole_showsAccessDenied() {
        setRole("ADMIN");
        FragmentScenario.launchInContainer(CaseListFragment.class, null,
                R.style.Theme_RoadRunner);

        // CaseListFragment only allows COMPLIANCE_REVIEWER, not ADMIN
        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }

    @Test
    public void caseList_workerRole_showsAccessDenied() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(CaseListFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }

    // ── CaseDetailFragment ───────────────────────────────────────────────────

    @Test
    public void caseDetail_reviewerRole_displaysDetailLayout() {
        setRole("COMPLIANCE_REVIEWER");
        Bundle args = new Bundle();
        args.putString("case_id", "nonexistent");
        FragmentScenario.launchInContainer(CaseDetailFragment.class, args,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.chip_case_type)).check(matches(isDisplayed()));
        onView(withId(R.id.tv_description)).check(matches(isDisplayed()));
    }

    @Test
    public void caseDetail_workerRole_showsAccessDenied() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(CaseDetailFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }

    // ── ReportFragment ───────────────────────────────────────────────────────

    @Test
    public void report_reviewerRole_displaysReportForm() {
        setRole("COMPLIANCE_REVIEWER");
        FragmentScenario.launchInContainer(ReportFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.spinner_target_type)).check(matches(isDisplayed()));
        onView(withId(R.id.et_description)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_file_report)).check(matches(isDisplayed()));
    }

    @Test
    public void report_workerRole_displaysReportForm() {
        setRole("WORKER");
        FragmentScenario.launchInContainer(ReportFragment.class, null,
                R.style.Theme_RoadRunner);

        // Worker is also allowed to file reports
        onView(withId(R.id.btn_file_report)).check(matches(isDisplayed()));
    }

    @Test
    public void report_dispatcherRole_showsAccessDenied() {
        setRole("DISPATCHER");
        FragmentScenario.launchInContainer(ReportFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withText(containsString("Access denied")))
                .check(matches(isDisplayed()));
    }
}
