package com.roadrunner.dispatch;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented tests that exercise the production {@link RoleGuard#hasRole(String...)}
 * path against a real {@link SessionManager} and {@link ServiceLocator}.
 */
@RunWith(AndroidJUnit4.class)
public class RoleGuardTest {

    private SessionManager sessionManager;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceLocator locator = ServiceLocator.getInstance(context);
        sessionManager = locator.getSessionManager();
        sessionManager.clearSession();
    }

    // -----------------------------------------------------------------------
    // Admin routes
    // -----------------------------------------------------------------------

    @Test
    public void adminDashboard_adminAllowed() {
        setRole("ADMIN");
        assertTrue(RoleGuard.hasRole("ADMIN"));
    }

    @Test
    public void adminDashboard_nonAdminDenied() {
        setRole("DISPATCHER");
        assertFalse(RoleGuard.hasRole("ADMIN"));
        setRole("WORKER");
        assertFalse(RoleGuard.hasRole("ADMIN"));
        setRole("COMPLIANCE_REVIEWER");
        assertFalse(RoleGuard.hasRole("ADMIN"));
    }

    // -----------------------------------------------------------------------
    // Dispatch routes
    // -----------------------------------------------------------------------

    @Test
    public void dispatcherDashboard_dispatcherAndAdminAllowed() {
        setRole("DISPATCHER");
        assertTrue(RoleGuard.hasRole("DISPATCHER", "ADMIN"));
        setRole("ADMIN");
        assertTrue(RoleGuard.hasRole("DISPATCHER", "ADMIN"));
    }

    @Test
    public void dispatcherDashboard_workerAndComplianceDenied() {
        setRole("WORKER");
        assertFalse(RoleGuard.hasRole("DISPATCHER", "ADMIN"));
        setRole("COMPLIANCE_REVIEWER");
        assertFalse(RoleGuard.hasRole("DISPATCHER", "ADMIN"));
    }

    @Test
    public void workerDashboard_workerAllowed() {
        setRole("WORKER");
        assertTrue(RoleGuard.hasRole("WORKER"));
    }

    @Test
    public void workerDashboard_nonWorkerDenied() {
        setRole("ADMIN");
        assertFalse(RoleGuard.hasRole("WORKER"));
        setRole("DISPATCHER");
        assertFalse(RoleGuard.hasRole("WORKER"));
    }

    // -----------------------------------------------------------------------
    // Commerce routes
    // -----------------------------------------------------------------------

    @Test
    public void commerce_allCommerceRolesAllowed() {
        String[] allowed = {"ADMIN", "DISPATCHER", "WORKER"};
        setRole("ADMIN");
        assertTrue(RoleGuard.hasRole(allowed));
        setRole("DISPATCHER");
        assertTrue(RoleGuard.hasRole(allowed));
        setRole("WORKER");
        assertTrue(RoleGuard.hasRole(allowed));
    }

    @Test
    public void commerce_complianceDenied() {
        setRole("COMPLIANCE_REVIEWER");
        assertFalse(RoleGuard.hasRole("ADMIN", "DISPATCHER", "WORKER"));
    }

    // -----------------------------------------------------------------------
    // Compliance routes
    // -----------------------------------------------------------------------

    @Test
    public void compliance_complianceAndAdminAllowed() {
        setRole("COMPLIANCE_REVIEWER");
        assertTrue(RoleGuard.hasRole("COMPLIANCE_REVIEWER", "ADMIN"));
        setRole("ADMIN");
        assertTrue(RoleGuard.hasRole("COMPLIANCE_REVIEWER", "ADMIN"));
    }

    @Test
    public void compliance_dispatcherAndWorkerDenied() {
        setRole("DISPATCHER");
        assertFalse(RoleGuard.hasRole("COMPLIANCE_REVIEWER", "ADMIN"));
        setRole("WORKER");
        assertFalse(RoleGuard.hasRole("COMPLIANCE_REVIEWER", "ADMIN"));
    }

    @Test
    public void caseList_complianceOnly() {
        setRole("COMPLIANCE_REVIEWER");
        assertTrue(RoleGuard.hasRole("COMPLIANCE_REVIEWER"));
        setRole("ADMIN");
        assertFalse(RoleGuard.hasRole("COMPLIANCE_REVIEWER"));
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    public void noSession_alwaysDenied() {
        sessionManager.clearSession();
        assertFalse(RoleGuard.hasRole("ADMIN", "DISPATCHER", "WORKER", "COMPLIANCE_REVIEWER"));
    }

    @Test
    public void currentRole_returnsSessionRole() {
        setRole("DISPATCHER");
        assertEquals("DISPATCHER", RoleGuard.currentRole());
    }

    @Test
    public void currentRole_noSession_returnsEmpty() {
        sessionManager.clearSession();
        assertEquals("", RoleGuard.currentRole());
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private void setRole(String role) {
        sessionManager.clearSession();
        sessionManager.createSession("user1", "org1", role, "testuser");
    }
}
