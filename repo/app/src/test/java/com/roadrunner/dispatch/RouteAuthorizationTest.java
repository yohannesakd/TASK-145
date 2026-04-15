package com.roadrunner.dispatch;

import com.roadrunner.dispatch.presentation.common.RoleGuard;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests documenting which roles each fragment/route authorizes.
 *
 * <p>Every assertion delegates to {@link RoleGuard#matchesRole(String, String...)} so
 * the test exercises the same matching logic used in production. The full
 * Android-dependent path ({@link RoleGuard#hasRole(String...)}) is covered by
 * the instrumented {@code RoleGuardTest} in {@code androidTest}.
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
        assertFalse(RoleGuard.matchesRole(null, allowed));
    }

    @Test
    public void emptyRole_alwaysDenied() {
        String[] allowed = {"ADMIN", "DISPATCHER", "WORKER", "COMPLIANCE_REVIEWER"};
        assertFalse(RoleGuard.matchesRole("", allowed));
    }

    @Test
    public void unknownRole_alwaysDenied() {
        String[] allowed = {"ADMIN"};
        assertFalse(RoleGuard.matchesRole("SUPER_ADMIN", allowed));
        assertFalse(RoleGuard.matchesRole("admin", allowed)); // case sensitive
    }

    @Test
    public void caseSensitive_rolesMustMatch() {
        String[] allowed = {"ADMIN"};
        assertFalse(RoleGuard.matchesRole("Admin", allowed));
        assertFalse(RoleGuard.matchesRole("admin", allowed));
        assertTrue(RoleGuard.matchesRole("ADMIN", allowed));
    }

    // -----------------------------------------------------------------------
    // Helpers — delegate to RoleGuard.matchesRole (production code)
    // -----------------------------------------------------------------------

    private static void assertAllowed(String[] allowedRoles, String... rolesToTest) {
        for (String role : rolesToTest) {
            assertTrue("Role " + role + " should be allowed",
                    RoleGuard.matchesRole(role, allowedRoles));
        }
    }

    private static void assertDenied(String[] allowedRoles, String... rolesToTest) {
        for (String role : rolesToTest) {
            assertFalse("Role " + role + " should be denied",
                    RoleGuard.matchesRole(role, allowedRoles));
        }
    }
}
