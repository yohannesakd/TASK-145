package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Session;
import com.roadrunner.dispatch.core.domain.usecase.LoginUseCase;
import com.roadrunner.dispatch.core.domain.usecase.RegisterUserUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.repository.UserRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.WorkerRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * End-to-end instrumented test for the login and role-routing flow.
 * Seeds credentials via RegisterUserUseCase, performs login via LoginUseCase,
 * creates a session via SessionManager, and verifies role-based navigation
 * readiness for each of the four roles.
 */
@RunWith(AndroidJUnit4.class)
public class LoginFlowIntegrationTest {

    private AppDatabase db;
    private SessionManager sessionManager;
    private LoginUseCase loginUseCase;
    private RegisterUserUseCase registerUseCase;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        UserRepositoryImpl userRepo = new UserRepositoryImpl(db.userDao());
        WorkerRepositoryImpl workerRepo = new WorkerRepositoryImpl(db.workerDao(), db.reputationEventDao());
        loginUseCase = new LoginUseCase(userRepo);
        registerUseCase = new RegisterUserUseCase(userRepo, workerRepo);
        sessionManager = new SessionManager(context);
        sessionManager.clearSession();
    }

    @After
    public void tearDown() {
        sessionManager.clearSession();
        db.close();
    }

    // -----------------------------------------------------------------------
    // Registration + Login per role
    // -----------------------------------------------------------------------

    @Test
    public void loginAsAdmin_sessionHasAdminRole() {
        register("admin_user", "Admin12345678", "ADMIN");
        Session session = login("admin_user", "Admin12345678");
        assertNotNull(session);
        assertEquals("ADMIN", session.role);
        sessionManager.createSession(session.userId, session.orgId, session.role, "admin_user");
        assertTrue(sessionManager.isLoggedIn());
        assertEquals("ADMIN", sessionManager.getRole());
    }

    @Test
    public void loginAsDispatcher_sessionHasDispatcherRole() {
        register("disp_user", "Dispatcher1234", "DISPATCHER");
        Session session = login("disp_user", "Dispatcher1234");
        assertNotNull(session);
        assertEquals("DISPATCHER", session.role);
        sessionManager.createSession(session.userId, session.orgId, session.role, "disp_user");
        assertEquals("DISPATCHER", sessionManager.getRole());
    }

    @Test
    public void loginAsWorker_sessionHasWorkerRole() {
        register("work_user", "Worker12345678", "WORKER");
        Session session = login("work_user", "Worker12345678");
        assertNotNull(session);
        assertEquals("WORKER", session.role);
        sessionManager.createSession(session.userId, session.orgId, session.role, "work_user");
        assertEquals("WORKER", sessionManager.getRole());
    }

    @Test
    public void loginAsComplianceReviewer_sessionHasComplianceRole() {
        register("rev_user", "Reviewer12345678", "COMPLIANCE_REVIEWER");
        Session session = login("rev_user", "Reviewer12345678");
        assertNotNull(session);
        assertEquals("COMPLIANCE_REVIEWER", session.role);
        sessionManager.createSession(session.userId, session.orgId, session.role, "rev_user");
        assertEquals("COMPLIANCE_REVIEWER", sessionManager.getRole());
    }

    // -----------------------------------------------------------------------
    // Invalid credentials
    // -----------------------------------------------------------------------

    @Test
    public void login_wrongPassword_fails() {
        register("user1", "CorrectPass1234", "ADMIN");
        Result<Session> result = loginUseCase.execute("user1", "WrongPassword99");
        assertFalse(result.isSuccess());
    }

    @Test
    public void login_nonExistentUser_fails() {
        Result<Session> result = loginUseCase.execute("ghost", "Password1234");
        assertFalse(result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Lockout
    // -----------------------------------------------------------------------

    @Test
    public void login_fiveFailedAttempts_locksAccount() {
        register("locktest", "GoodPassword1234", "WORKER");
        for (int i = 0; i < 5; i++) {
            loginUseCase.execute("locktest", "wrongpass1234");
        }
        Result<Session> result = loginUseCase.execute("locktest", "GoodPassword1234");
        assertFalse("Account should be locked", result.isSuccess());
    }

    // -----------------------------------------------------------------------
    // Session lifecycle
    // -----------------------------------------------------------------------

    @Test
    public void logout_clearsAllSessionFields() {
        register("user1", "Password12345678", "ADMIN");
        Session session = login("user1", "Password12345678");
        sessionManager.createSession(session.userId, session.orgId, session.role, "user1");
        assertTrue(sessionManager.isLoggedIn());

        sessionManager.clearSession();
        assertFalse(sessionManager.isLoggedIn());
        assertNull(sessionManager.getUserId());
        assertNull(sessionManager.getRole());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void register(String username, String password, String role) {
        Result<?> result = registerUseCase.execute(
                "org1", username, password, role);
        assertTrue("Registration should succeed: " + result.getFirstError(),
                result.isSuccess());
    }

    private Session login(String username, String password) {
        Result<Session> result = loginUseCase.execute(username, password);
        assertTrue("Login should succeed: " + result.getFirstError(),
                result.isSuccess());
        return result.getData();
    }
}
