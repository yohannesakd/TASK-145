package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.usecase.LoginUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.repository.UserRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;
import com.roadrunner.dispatch.presentation.auth.LoginViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented tests for {@link LoginViewModel} wired to real Room DB
 * and SessionManager. Verifies login success/failure state transitions
 * and session persistence.
 */
@RunWith(AndroidJUnit4.class)
public class LoginViewModelTest {

    private AppDatabase db;
    private SessionManager sessionManager;
    private LoginViewModel viewModel;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        UserRepositoryImpl userRepo = new UserRepositoryImpl(db.userDao());
        LoginUseCase loginUseCase = new LoginUseCase(userRepo);
        sessionManager = new SessionManager(context);
        sessionManager.clearSession();
        viewModel = new LoginViewModel(loginUseCase, sessionManager);
    }

    @After
    public void tearDown() {
        sessionManager.clearSession();
        db.close();
    }

    // -----------------------------------------------------------------------
    // Session state
    // -----------------------------------------------------------------------

    @Test
    public void isLoggedIn_noSession_returnsFalse() {
        assertFalse(viewModel.isLoggedIn());
    }

    @Test
    public void getCurrentRole_noSession_returnsNull() {
        assertNull(viewModel.getCurrentRole());
    }

    @Test
    public void logout_clearsSession() {
        sessionManager.createSession("u1", "org1", "ADMIN", "admin");
        assertTrue(viewModel.isLoggedIn());
        viewModel.logout();
        assertFalse(viewModel.isLoggedIn());
    }

    @Test
    public void isLoggedIn_afterSessionCreation_returnsTrue() {
        sessionManager.createSession("u1", "org1", "WORKER", "worker");
        assertTrue(viewModel.isLoggedIn());
        assertEquals("WORKER", viewModel.getCurrentRole());
    }

    // -----------------------------------------------------------------------
    // Login flow
    // -----------------------------------------------------------------------

    @Test
    public void login_invalidCredentials_postsError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] observedError = {null};

        // Observe on instrumentation thread
        viewModel.getLoginState().observeForever(state -> {
            if (state != null && state.status == LoginViewModel.LoginState.Status.ERROR) {
                observedError[0] = state.error;
                latch.countDown();
            }
        });

        viewModel.login("nonexistent", "wrongpass");
        assertTrue("Login should complete within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observedError[0]);
    }

    @Test
    public void login_emptyUsername_postsError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.getLoginState().observeForever(state -> {
            if (state != null && state.status == LoginViewModel.LoginState.Status.ERROR) {
                latch.countDown();
            }
        });

        viewModel.login("", "password");
        assertTrue("Login should complete within 5s", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void getSessionManager_returnsNonNull() {
        assertNotNull(viewModel.getSessionManager());
    }
}
