package com.roadrunner.dispatch;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.infrastructure.security.SessionManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * On-device integration tests for {@link SessionManager}.
 * Verifies that EncryptedSharedPreferences correctly persists and retrieves
 * session data, and that role-based authorization data is faithfully stored.
 */
@RunWith(AndroidJUnit4.class)
public class SessionManagerTest {

    private SessionManager sessionManager;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        sessionManager = new SessionManager(context);
        sessionManager.clearSession();
    }

    // -----------------------------------------------------------------------
    // Session lifecycle tests
    // -----------------------------------------------------------------------

    @Test
    public void noSession_isLoggedInReturnsFalse() {
        assertFalse(sessionManager.isLoggedIn());
    }

    @Test
    public void createSession_isLoggedInReturnsTrue() {
        sessionManager.createSession("u1", "org1", "ADMIN", "admin_user");

        assertTrue(sessionManager.isLoggedIn());
    }

    @Test
    public void clearSession_afterCreate_isLoggedInReturnsFalse() {
        sessionManager.createSession("u1", "org1", "ADMIN", "admin_user");
        sessionManager.clearSession();

        assertFalse(sessionManager.isLoggedIn());
    }

    // -----------------------------------------------------------------------
    // Role storage tests (supports RoleGuard verification)
    // -----------------------------------------------------------------------

    @Test
    public void adminRole_storedAndRetrieved() {
        sessionManager.createSession("u1", "org1", "ADMIN", "admin_user");

        assertEquals("ADMIN", sessionManager.getRole());
    }

    @Test
    public void dispatcherRole_storedAndRetrieved() {
        sessionManager.createSession("u2", "org1", "DISPATCHER", "dispatch_user");

        assertEquals("DISPATCHER", sessionManager.getRole());
    }

    @Test
    public void workerRole_storedAndRetrieved() {
        sessionManager.createSession("u3", "org1", "WORKER", "worker_user");

        assertEquals("WORKER", sessionManager.getRole());
    }

    @Test
    public void complianceReviewerRole_storedAndRetrieved() {
        sessionManager.createSession("u4", "org1", "COMPLIANCE_REVIEWER", "reviewer_user");

        assertEquals("COMPLIANCE_REVIEWER", sessionManager.getRole());
    }

    // -----------------------------------------------------------------------
    // Session data integrity tests
    // -----------------------------------------------------------------------

    @Test
    public void allSessionFields_storedCorrectly() {
        sessionManager.createSession("user-42", "org-7", "WORKER", "jdoe");

        assertEquals("user-42", sessionManager.getUserId());
        assertEquals("org-7", sessionManager.getOrgId());
        assertEquals("WORKER", sessionManager.getRole());
        assertEquals("jdoe", sessionManager.getUsername());
        assertTrue(sessionManager.getSessionCreatedAt() > 0);
    }

    @Test
    public void sessionOverwrite_replacesPreviousValues() {
        sessionManager.createSession("u1", "org1", "ADMIN", "admin");
        sessionManager.createSession("u2", "org2", "WORKER", "worker");

        assertEquals("u2", sessionManager.getUserId());
        assertEquals("org2", sessionManager.getOrgId());
        assertEquals("WORKER", sessionManager.getRole());
        assertEquals("worker", sessionManager.getUsername());
    }

    @Test
    public void clearedSession_allFieldsNull() {
        sessionManager.createSession("u1", "org1", "ADMIN", "admin");
        sessionManager.clearSession();

        assertNull(sessionManager.getUserId());
        assertNull(sessionManager.getOrgId());
        assertNull(sessionManager.getRole());
        assertNull(sessionManager.getUsername());
    }

    // -----------------------------------------------------------------------
    // Org isolation — data bound to session
    // -----------------------------------------------------------------------

    @Test
    public void orgId_isolatedPerSession() {
        sessionManager.createSession("u1", "org-A", "ADMIN", "admin");
        assertEquals("org-A", sessionManager.getOrgId());

        sessionManager.createSession("u2", "org-B", "WORKER", "worker");
        assertEquals("org-B", sessionManager.getOrgId());
    }
}
