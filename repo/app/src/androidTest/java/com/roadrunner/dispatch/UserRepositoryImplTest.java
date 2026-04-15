package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.core.domain.model.User;
import com.roadrunner.dispatch.core.domain.model.UserAuthInfo;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.UserDao;
import com.roadrunner.dispatch.infrastructure.db.entity.UserEntity;
import com.roadrunner.dispatch.infrastructure.repository.UserRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UserRepositoryImplTest {

    private AppDatabase db;
    private UserDao userDao;
    private UserRepositoryImpl repo;
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        userDao = db.userDao();
        repo = new UserRepositoryImpl(userDao);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -----------------------------------------------------------------------
    // Insert and query
    // -----------------------------------------------------------------------

    @Test
    public void insertUser_and_findByUsername() {
        repo.insertUser("u1", "org1", "alice", "hash123", "salt123", "ADMIN");
        User user = repo.findByUsername("alice");
        assertNotNull(user);
        assertEquals("u1", user.id);
        assertEquals("org1", user.orgId);
        assertEquals("alice", user.username);
        assertEquals("ADMIN", user.role);
        assertTrue(user.isActive);
    }

    @Test
    public void findByUsername_nonExistent_returnsNull() {
        assertNull(repo.findByUsername("ghost"));
    }

    @Test
    public void findById_returnsUser() {
        repo.insertUser("u1", "org1", "bob", "hash", "salt", "WORKER");
        User user = repo.findById("u1");
        assertNotNull(user);
        assertEquals("bob", user.username);
    }

    @Test
    public void getAuthInfo_returnsCredentials() {
        repo.insertUser("u1", "org1", "carol", "hashVal", "saltVal", "DISPATCHER");
        UserAuthInfo auth = repo.getAuthInfo("carol");
        assertNotNull(auth);
        assertEquals("u1", auth.userId);
        assertEquals("hashVal", auth.passwordHash);
        assertEquals("saltVal", auth.passwordSalt);
        assertEquals("DISPATCHER", auth.role);
        assertEquals(0, auth.failedAttempts);
    }

    // -----------------------------------------------------------------------
    // Lockout
    // -----------------------------------------------------------------------

    @Test
    public void recordFailedAttempt_updatesCountAndLockTime() {
        repo.insertUser("u1", "org1", "dave", "h", "s", "ADMIN");
        long lockUntil = NOW + 900_000L;
        repo.recordFailedAttempt("u1", 3, lockUntil);

        UserAuthInfo auth = repo.getAuthInfo("dave");
        assertNotNull(auth);
        assertEquals(3, auth.failedAttempts);
        assertEquals(lockUntil, auth.lockedUntil);
    }

    @Test
    public void resetFailedAttempts_clearsCountAndLock() {
        repo.insertUser("u1", "org1", "eve", "h", "s", "WORKER");
        repo.recordFailedAttempt("u1", 5, NOW + 900_000L);
        repo.resetFailedAttempts("u1");

        UserAuthInfo auth = repo.getAuthInfo("eve");
        assertNotNull(auth);
        assertEquals(0, auth.failedAttempts);
        assertEquals(0, auth.lockedUntil);
    }

    // -----------------------------------------------------------------------
    // Duplicate prevention
    // -----------------------------------------------------------------------

    @Test(expected = Exception.class)
    public void insertUser_duplicateId_throws() {
        repo.insertUser("u1", "org1", "frank", "h", "s", "ADMIN");
        repo.insertUser("u1", "org2", "frank2", "h2", "s2", "WORKER");
    }
}
