package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Session;
import com.roadrunner.dispatch.core.domain.model.User;
import com.roadrunner.dispatch.core.domain.model.UserAuthInfo;
import com.roadrunner.dispatch.core.domain.repository.UserRepository;
import com.roadrunner.dispatch.core.domain.usecase.LoginUseCase;
import com.roadrunner.dispatch.core.util.PasswordHasher;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class LoginUseCaseTest {

    private StubUserRepository userRepo;
    private LoginUseCase useCase;

    // A real password to use in tests
    private static final String CORRECT_PASSWORD = "Correct_Password123";
    private static final String WRONG_PASSWORD = "WrongPassword99";

    @Before
    public void setUp() {
        userRepo = new StubUserRepository();
        useCase = new LoginUseCase(userRepo);
    }

    // -----------------------------------------------------------------------
    // Helper: register a valid user
    // -----------------------------------------------------------------------

    private void registerUser(String username, String password, boolean active,
                               int failedAttempts, long lockedUntil) {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash(password, salt);
        userRepo.addUser(new UserAuthInfo(
                "uid-" + username, "org1", username, "WORKER",
                active, hash, salt, failedAttempts, lockedUntil));
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void correctCredentials_success_sessionReturned() {
        registerUser("alice", CORRECT_PASSWORD, true, 0, 0);
        Result<Session> result = useCase.execute("alice", CORRECT_PASSWORD);
        assertTrue(result.isSuccess());
        Session session = result.getData();
        assertNotNull(session);
        assertEquals("uid-alice", session.userId);
        assertEquals("org1", session.orgId);
    }

    @Test
    public void wrongPassword_failure_invalidCredentials() {
        registerUser("alice", CORRECT_PASSWORD, true, 0, 0);
        Result<Session> result = useCase.execute("alice", WRONG_PASSWORD);
        assertFalse(result.isSuccess());
        assertTrue("Expected 'Invalid credentials'",
                result.getFirstError().contains("Invalid credentials"));
    }

    @Test
    public void unknownUsername_failure_sameMessageAsWrongPassword() {
        // Don't reveal whether username exists
        Result<Session> result = useCase.execute("nobody", CORRECT_PASSWORD);
        assertFalse(result.isSuccess());
        assertEquals("Invalid credentials", result.getFirstError());
    }

    @Test
    public void fifthFailedAttempt_accountLocked() {
        // 4 prior failures recorded
        registerUser("bob", CORRECT_PASSWORD, true, 4, 0);
        Result<Session> result = useCase.execute("bob", WRONG_PASSWORD);
        assertFalse(result.isSuccess());
        assertTrue("Expected lockout message",
                result.getFirstError().contains("locked") &&
                result.getFirstError().contains("15 minutes"));
    }

    @Test
    public void alreadyLockedAccount_returnsLockoutMessageWithRemainingTime() {
        long lockUntil = System.currentTimeMillis() + 10 * 60 * 1000L; // 10 minutes from now
        registerUser("carol", CORRECT_PASSWORD, true, 5, lockUntil);
        Result<Session> result = useCase.execute("carol", CORRECT_PASSWORD);
        assertFalse(result.isSuccess());
        assertTrue("Expected 'Account locked' message",
                result.getFirstError().contains("Account locked"));
        assertTrue("Expected remaining minutes in message",
                result.getFirstError().contains("minute"));
    }

    @Test
    public void lockoutExpired_successfulLogin() {
        // Lock expired 1 second ago
        long expiredLock = System.currentTimeMillis() - 1000L;
        registerUser("dave", CORRECT_PASSWORD, true, 5, expiredLock);
        Result<Session> result = useCase.execute("dave", CORRECT_PASSWORD);
        assertTrue("Login should succeed once lockout expires", result.isSuccess());
    }

    @Test
    public void emptyUsername_failure() {
        Result<Session> result = useCase.execute("", CORRECT_PASSWORD);
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("username"));
    }

    @Test
    public void whitespaceUsername_failure() {
        Result<Session> result = useCase.execute("   ", CORRECT_PASSWORD);
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("username"));
    }

    @Test
    public void emptyPassword_failure() {
        Result<Session> result = useCase.execute("alice", "");
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("password"));
    }

    @Test
    public void nullPassword_failure() {
        Result<Session> result = useCase.execute("alice", null);
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("password"));
    }

    @Test
    public void deactivatedAccount_failure() {
        registerUser("eve", CORRECT_PASSWORD, false, 0, 0);
        Result<Session> result = useCase.execute("eve", CORRECT_PASSWORD);
        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("deactivated"));
    }

    @Test
    public void correctLogin_resetsFailedAttempts() {
        registerUser("frank", CORRECT_PASSWORD, true, 2, 0);
        Result<Session> result = useCase.execute("frank", CORRECT_PASSWORD);
        assertTrue(result.isSuccess());
        // Verify resetFailedAttempts was called
        assertTrue(userRepo.resetCalledFor.contains("uid-frank"));
    }

    @Test
    public void failedLogin_incrementsAttemptCount() {
        registerUser("gina", CORRECT_PASSWORD, true, 1, 0);
        useCase.execute("gina", WRONG_PASSWORD);
        // Should have recorded attempt with count = 2
        assertEquals(2, (int) userRepo.lastFailedCount.getOrDefault("uid-gina", 0));
    }

    // -----------------------------------------------------------------------
    // Stub implementation
    // -----------------------------------------------------------------------

    private static class StubUserRepository implements UserRepository {
        private final Map<String, UserAuthInfo> byUsername = new HashMap<>();
        private final Map<String, UserAuthInfo> byId = new HashMap<>();
        final java.util.Set<String> resetCalledFor = new java.util.HashSet<>();
        final Map<String, Integer> lastFailedCount = new HashMap<>();

        void addUser(UserAuthInfo info) {
            byUsername.put(info.username, info);
            byId.put(info.userId, info);
        }

        @Override
        public UserAuthInfo getAuthInfo(String username) {
            return byUsername.get(username);
        }

        @Override
        public void recordFailedAttempt(String userId, int newFailedCount, long lockedUntil) {
            lastFailedCount.put(userId, newFailedCount);
            UserAuthInfo old = byId.get(userId);
            if (old != null) {
                UserAuthInfo updated = new UserAuthInfo(old.userId, old.orgId, old.username,
                        old.role, old.isActive, old.passwordHash, old.passwordSalt,
                        newFailedCount, lockedUntil);
                byId.put(userId, updated);
                byUsername.put(old.username, updated);
            }
        }

        @Override
        public void resetFailedAttempts(String userId) {
            resetCalledFor.add(userId);
            UserAuthInfo old = byId.get(userId);
            if (old != null) {
                UserAuthInfo updated = new UserAuthInfo(old.userId, old.orgId, old.username,
                        old.role, old.isActive, old.passwordHash, old.passwordSalt, 0, 0);
                byId.put(userId, updated);
                byUsername.put(old.username, updated);
            }
        }

        @Override
        public User findByUsername(String username) {
            UserAuthInfo info = byUsername.get(username);
            return info == null ? null : new User(info.userId, info.orgId, info.username, info.role, info.isActive);
        }

        @Override
        public User findById(String id) {
            UserAuthInfo info = byId.get(id);
            return info == null ? null : new User(info.userId, info.orgId, info.username, info.role, info.isActive);
        }

        @Override
        public void insertUser(String id, String orgId, String username, String passwordHash,
                               String passwordSalt, String role) {
            UserAuthInfo info = new UserAuthInfo(id, orgId, username, role, true,
                    passwordHash, passwordSalt, 0, 0);
            byUsername.put(username, info);
            byId.put(id, info);
        }

        @Override
        public LiveData<List<User>> getUsersByOrg(String orgId) { return null; }
    }
}
