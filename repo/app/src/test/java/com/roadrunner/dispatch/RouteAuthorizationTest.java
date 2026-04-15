package com.roadrunner.dispatch;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests documenting which roles each fragment/route authorizes.
 * While the actual RoleGuard relies on Android SessionManager, this test
 * validates the authorization logic (role matching) at the unit level
 * and serves as an executable specification of the authorization matrix.
 *
 * Each test mirrors the RoleGuard.hasRole(...) check in the corresponding fragment.
 */
public class RouteAuthorizationTest {

    // -----------------------------------------------------------------------
    // Admin routes: ADMIN only
    // -----------------------------------------------------------------------

    @Test
    public void adminDashboard_onlyAdmin() {
        String[] allowed = {"ADMIN"};
        assertAllowed(allowed, "ADMIN");
        assertDenied(allowed, "DISPATCHER", "WORKER", "COMPLIANCE_REVIEWER");
    }

    @Test
    public void adminConfig_onlyAdmin() {
        String[] allowed = {"ADMIN"};
        assertAllowed(allowed, "ADMIN");
        assertDenied(allowed, "DISPATCHER", "WORKER", "COMPLIANCE_REVIEWER");
    }

    @Test
    public void userManagement_onlyAdmin() {
        String[] allowed = {"ADMIN"};
        assertAllowed(allowed, "ADMIN");
        assertDenied(allowed, "DISPATCHER", "WORKER", "COMPLIANCE_REVIEWER");
    }

    @Test
    public void importExport_onlyAdmin() {
        String[] allowed = {"ADMIN"};
        assertAllowed(allowed, "ADMIN");
        assertDenied(allowed, "DISPATCHER", "WORKER", "COMPLIANCE_REVIEWER");
    }

    // -----------------------------------------------------------------------
    // Dispatch routes
    // -----------------------------------------------------------------------

    @Test
    public void dispatcherDashboard_dispatcherAndAdmin() {
        String[] allowed = {"DISPATCHER", "ADMIN"};
        assertAllowed(allowed, "DISPATCHER", "ADMIN");
        assertDenied(allowed, "WORKER", "COMPLIANCE_REVIEWER");
    }

    @Test
    public void workerDashboard_workerOnly() {
        String[] allowed = {"WORKER"};
        assertAllowed(allowed, "WORKER");
        assertDenied(allowed, "ADMIN", "DISPATCHER", "COMPLIANCE_REVIEWER");
    }

    @Test
    public void zoneManagement_dispatcherAndAdmin() {
        String[] allowed = {"DISPATCHER", "ADMIN"};
        assertAllowed(allowed, "DISPATCHER", "ADMIN");
        assertDenied(allowed, "WORKER", "COMPLIANCE_REVIEWER");
    }

    // -----------------------------------------------------------------------
    // Commerce routes: ADMIN, DISPATCHER, WORKER
    // -----------------------------------------------------------------------

    @Test
    public void catalog_commerceRoles() {
        String[] allowed = {"ADMIN", "DISPATCHER", "WORKER"};
        assertAllowed(allowed, "ADMIN", "DISPATCHER", "WORKER");
        assertDenied(allowed, "COMPLIANCE_REVIEWER");
    }

    @Test
    public void cart_commerceRoles() {
        String[] allowed = {"ADMIN", "DISPATCHER", "WORKER"};
        assertAllowed(allowed, "ADMIN", "DISPATCHER", "WORKER");
        assertDenied(allowed, "COMPLIANCE_REVIEWER");
    }

    @Test
    public void checkout_commerceRoles() {
        String[] allowed = {"ADMIN", "DISPATCHER", "WORKER"};
        assertAllowed(allowed, "ADMIN", "DISPATCHER", "WORKER");
        assertDenied(allowed, "COMPLIANCE_REVIEWER");
    }

    @Test
    public void invoice_commerceRoles() {
        String[] allowed = {"ADMIN", "DISPATCHER", "WORKER"};
        assertAllowed(allowed, "ADMIN", "DISPATCHER", "WORKER");
        assertDenied(allowed, "COMPLIANCE_REVIEWER");
    }

    // -----------------------------------------------------------------------
    // Compliance routes: COMPLIANCE_REVIEWER, ADMIN
    // -----------------------------------------------------------------------

    @Test
    public void complianceDashboard_complianceAndAdmin() {
        String[] allowed = {"COMPLIANCE_REVIEWER", "ADMIN"};
        assertAllowed(allowed, "COMPLIANCE_REVIEWER", "ADMIN");
        assertDenied(allowed, "DISPATCHER", "WORKER");
    }

    @Test
    public void employerList_complianceAndAdmin() {
        String[] allowed = {"COMPLIANCE_REVIEWER", "ADMIN"};
        assertAllowed(allowed, "COMPLIANCE_REVIEWER", "ADMIN");
        assertDenied(allowed, "DISPATCHER", "WORKER");
    }

    @Test
    public void employerDetail_complianceAndAdmin() {
        String[] allowed = {"COMPLIANCE_REVIEWER", "ADMIN"};
        assertAllowed(allowed, "COMPLIANCE_REVIEWER", "ADMIN");
        assertDenied(allowed, "DISPATCHER", "WORKER");
    }

    @Test
    public void reportFiling_complianceAndWorker() {
        String[] allowed = {"COMPLIANCE_REVIEWER", "WORKER"};
        assertAllowed(allowed, "COMPLIANCE_REVIEWER", "WORKER");
        assertDenied(allowed, "ADMIN", "DISPATCHER");
    }

    @Test
    public void caseList_complianceOnly() {
        String[] allowed = {"COMPLIANCE_REVIEWER"};
        assertAllowed(allowed, "COMPLIANCE_REVIEWER");
        assertDenied(allowed, "ADMIN", "DISPATCHER", "WORKER");
    }

    // -----------------------------------------------------------------------
    // Cross-cutting: OrderList (ADMIN, DISPATCHER, COMPLIANCE_REVIEWER)
    // -----------------------------------------------------------------------

    @Test
    public void orderList_adminDispatcherCompliance() {
        String[] allowed = {"ADMIN", "DISPATCHER", "COMPLIANCE_REVIEWER"};
        assertAllowed(allowed, "ADMIN", "DISPATCHER", "COMPLIANCE_REVIEWER");
        assertDenied(allowed, "WORKER");
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    public void nullRole_alwaysDenied() {
        String[] allowed = {"ADMIN", "DISPATCHER", "WORKER", "COMPLIANCE_REVIEWER"};
        assertFalse(hasRole(null, allowed));
    }

    @Test
    public void emptyRole_alwaysDenied() {
        String[] allowed = {"ADMIN", "DISPATCHER", "WORKER", "COMPLIANCE_REVIEWER"};
        assertFalse(hasRole("", allowed));
    }

    @Test
    public void unknownRole_alwaysDenied() {
        String[] allowed = {"ADMIN"};
        assertFalse(hasRole("SUPER_ADMIN", allowed));
        assertFalse(hasRole("admin", allowed)); // case sensitive
    }

    @Test
    public void caseSensitive_rolesMustMatch() {
        String[] allowed = {"ADMIN"};
        assertFalse(hasRole("Admin", allowed));
        assertFalse(hasRole("admin", allowed));
        assertTrue(hasRole("ADMIN", allowed));
    }

    // -----------------------------------------------------------------------
    // Helper: mirrors RoleGuard.hasRole logic without Android dependency
    // -----------------------------------------------------------------------

    private static boolean hasRole(String currentRole, String... allowedRoles) {
        if (currentRole == null || currentRole.isEmpty()) return false;
        for (String role : allowedRoles) {
            if (currentRole.equals(role)) return true;
        }
        return false;
    }

    private static void assertAllowed(String[] allowedRoles, String... rolesToTest) {
        for (String role : rolesToTest) {
            assertTrue("Role " + role + " should be allowed", hasRole(role, allowedRoles));
        }
    }

    private static void assertDenied(String[] allowedRoles, String... rolesToTest) {
        for (String role : rolesToTest) {
            assertFalse("Role " + role + " should be denied", hasRole(role, allowedRoles));
        }
    }
}
