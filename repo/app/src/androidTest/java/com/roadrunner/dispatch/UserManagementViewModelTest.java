package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.User;
import com.roadrunner.dispatch.core.domain.usecase.RegisterUserUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.repository.UserRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.repository.WorkerRepositoryImpl;
import com.roadrunner.dispatch.presentation.admin.UserManagementViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented tests for {@link UserManagementViewModel} wired to real Room DB.
 */
@RunWith(AndroidJUnit4.class)
public class UserManagementViewModelTest {

    private AppDatabase db;
    private UserManagementViewModel viewModel;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        UserRepositoryImpl userRepo = new UserRepositoryImpl(db.userDao());
        WorkerRepositoryImpl workerRepo = new WorkerRepositoryImpl(db.workerDao(), db.reputationEventDao());

        RegisterUserUseCase registerUseCase = new RegisterUserUseCase(userRepo, workerRepo);
        viewModel = new UserManagementViewModel(registerUseCase);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void registerUser_validInput_postsSuccess() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final Result<User>[] observed = new Result[]{null};

        viewModel.getRegistrationResult().observeForever(result -> {
            if (result != null) {
                observed[0] = result;
                latch.countDown();
            }
        });

        viewModel.registerUser("org1", "newuser", "SecurePass1234", "WORKER");

        assertTrue("Registration should complete within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertTrue(observed[0].isSuccess());
        assertEquals("newuser", observed[0].getData().username);
    }

    @Test
    public void registerUser_shortPassword_postsFailure() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final Result<User>[] observed = new Result[]{null};

        viewModel.getRegistrationResult().observeForever(result -> {
            if (result != null) {
                observed[0] = result;
                latch.countDown();
            }
        });

        viewModel.registerUser("org1", "user", "short", "WORKER");

        assertTrue("Registration should complete within 5s", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(observed[0]);
        assertFalse(observed[0].isSuccess());
    }

    @Test
    public void registerUser_duplicateUsername_postsFailure() throws InterruptedException {
        CountDownLatch latch1 = new CountDownLatch(1);
        viewModel.getRegistrationResult().observeForever(r -> {
            if (r != null) latch1.countDown();
        });
        viewModel.registerUser("org1", "dupe", "SecurePass1234", "WORKER");
        assertTrue(latch1.await(5, TimeUnit.SECONDS));

        // Re-register same username
        CountDownLatch latch2 = new CountDownLatch(1);
        final Result<User>[] observed = new Result[]{null};
        // Need a fresh observer since the old one already counted down
        viewModel = new UserManagementViewModel(
                new RegisterUserUseCase(
                        new UserRepositoryImpl(db.userDao()),
                        new WorkerRepositoryImpl(db.workerDao(), db.reputationEventDao())));
        viewModel.getRegistrationResult().observeForever(result -> {
            if (result != null) {
                observed[0] = result;
                latch2.countDown();
            }
        });
        viewModel.registerUser("org1", "dupe", "SecurePass1234", "WORKER");

        assertTrue("Second registration should complete within 5s", latch2.await(5, TimeUnit.SECONDS));
        assertFalse(observed[0].isSuccess());
    }

    @Test
    public void registerUser_withZoneId_postsSuccess() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final Result<User>[] observed = new Result[]{null};

        viewModel.getRegistrationResult().observeForever(result -> {
            if (result != null) {
                observed[0] = result;
                latch.countDown();
            }
        });

        viewModel.registerUser("org1", "zoneworker", "SecurePass1234", "WORKER", "zone1");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(observed[0].isSuccess());
    }

    @Test
    public void getRegistrationResult_returnsLiveData() {
        assertNotNull(viewModel.getRegistrationResult());
    }
}
