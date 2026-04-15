package com.roadrunner.dispatch;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.ReputationEvent;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.User;
import com.roadrunner.dispatch.core.domain.model.UserAuthInfo;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.core.domain.repository.UserRepository;
import com.roadrunner.dispatch.core.domain.repository.WorkerRepository;
import com.roadrunner.dispatch.core.domain.usecase.RegisterUserUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class RegisterUserUseCaseTest {

    private StubUserRepository userRepo;
    private RegisterUserUseCase useCase;

    // A password that is exactly 12 characters — the minimum allowed
    private static final String MIN_VALID_PASSWORD = "password12ab";
    // A password that is 11 characters — one short of the minimum
    private static final String SHORT_PASSWORD = "password12a";

    @Before
    public void setUp() {
        userRepo = new StubUserRepository();
        useCase  = new RegisterUserUseCase(userRepo);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void validRegistration_success() {
        Result<User> result = useCase.execute("org1", "alice", MIN_VALID_PASSWORD, "WORKER");

        assertTrue(result.isSuccess());
        assertEquals("alice", result.getData().username);
        assertEquals("WORKER", result.getData().role);
        assertEquals("org1", result.getData().orgId);
        assertTrue(result.getData().isActive);
    }

    @Test
    public void shortPassword_11Chars_failure() {
        Result<User> result = useCase.execute("org1", "bob", SHORT_PASSWORD, "WORKER");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("12"));
    }

    @Test
    public void password12Chars_success() {
        Result<User> result = useCase.execute("org1", "carol", MIN_VALID_PASSWORD, "DISPATCHER");

        assertTrue(result.isSuccess());
    }

    @Test
    public void emptyUsername_failure() {
        Result<User> result = useCase.execute("org1", "   ", MIN_VALID_PASSWORD, "WORKER");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("username"));
    }

    @Test
    public void duplicateUsername_failure() {
        // Register once successfully
        useCase.execute("org1", "dave", MIN_VALID_PASSWORD, "WORKER");

        // Try again with the same username
        Result<User> result = useCase.execute("org1", "dave", MIN_VALID_PASSWORD, "WORKER");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("taken") ||
                   result.getFirstError().toLowerCase().contains("already"));
    }

    @Test
    public void invalidRole_failure() {
        Result<User> result = useCase.execute("org1", "eve", MIN_VALID_PASSWORD, "HACKER");

        assertFalse(result.isSuccess());
        assertTrue(result.getFirstError().toLowerCase().contains("role"));
    }

    @Test
    public void passwordIsHashed_hashNotEqualToPlaintext() {
        Result<User> result = useCase.execute("org1", "frank", MIN_VALID_PASSWORD, "ADMIN");

        assertTrue(result.isSuccess());
        // The stored hash should not be the plaintext password
        assertFalse(userRepo.insertedHashes.isEmpty());
        String storedHash = userRepo.insertedHashes.get("frank");
        assertNotNull(storedHash);
        assertNotEquals(MIN_VALID_PASSWORD, storedHash);
    }

    @Test
    public void workerRole_createsWorkerProfile() {
        StubWorkerRepository workerRepo = new StubWorkerRepository();
        RegisterUserUseCase useCaseWithWorker = new RegisterUserUseCase(userRepo, workerRepo);
        Result<User> result = useCaseWithWorker.execute("org1", "newworker", "WorkerPass1234", "WORKER");
        assertTrue(result.isSuccess());
        assertEquals("WORKER", result.getData().role);
        assertFalse(workerRepo.insertedWorkers.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Stub implementation
    // -----------------------------------------------------------------------

    private static class StubUserRepository implements UserRepository {
        private final Map<String, User> byUsername = new HashMap<>();
        /** username → hash stored during insertUser */
        final Map<String, String> insertedHashes = new HashMap<>();

        @Override
        public User findByUsername(String username) {
            return byUsername.get(username.trim());
        }

        @Override
        public void insertUser(String id, String orgId, String username,
                String passwordHash, String passwordSalt, String role) {
            User u = new User(id, orgId, username, role, true);
            byUsername.put(username, u);
            insertedHashes.put(username, passwordHash);
        }

        @Override public User findById(String id) { return null; }
        @Override public UserAuthInfo getAuthInfo(String username) { return null; }
        @Override public void recordFailedAttempt(String userId, int newFailedCount, long lockedUntil) {}
        @Override public void resetFailedAttempts(String userId) {}
        @Override public LiveData<List<User>> getUsersByOrg(String orgId) { return null; }
    }

    static class StubWorkerRepository implements WorkerRepository {
        List<Worker> insertedWorkers = new ArrayList<>();
        @Override public void insert(Worker w) { insertedWorkers.add(w); }
        @Override public Worker getByIdScoped(String id, String orgId) { return null; }
        @Override public Worker getByUserIdScoped(String userId, String orgId) { return null; }
        @Override public void update(Worker worker) {}
        @Override public void adjustWorkload(String workerId, int delta, String orgId) {}
        @Override public void updateReputationScore(String workerId, double score, String orgId) {}
        @Override public void addReputationEvent(ReputationEvent event) {}
        @Override public double getAverageReputation(String workerId) { return 0.0; }
        @Override public List<Worker> getWorkersByStatus(String orgId, String status) { return new ArrayList<>(); }
        @Override public LiveData<List<Worker>> getWorkers(String orgId) { return null; }
        @Override public LiveData<List<ReputationEvent>> getReputationEvents(String workerId) { return null; }
    }
}
