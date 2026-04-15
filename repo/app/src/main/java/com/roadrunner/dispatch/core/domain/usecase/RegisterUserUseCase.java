package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.User;
import com.roadrunner.dispatch.core.domain.model.Worker;
import com.roadrunner.dispatch.core.domain.repository.UserRepository;
import com.roadrunner.dispatch.core.domain.repository.WorkerRepository;
import com.roadrunner.dispatch.core.util.PasswordHasher;
import java.util.UUID;

public class RegisterUserUseCase {
    private final UserRepository userRepository;
    private final WorkerRepository workerRepository;

    public RegisterUserUseCase(UserRepository userRepository, WorkerRepository workerRepository) {
        this.userRepository = userRepository;
        this.workerRepository = workerRepository;
    }

    /** Backward-compatible constructor; worker profiles will NOT be auto-created. */
    public RegisterUserUseCase(UserRepository userRepository) {
        this(userRepository, null);
    }

    public Result<User> execute(String orgId, String username, String password, String role) {
        return execute(orgId, username, password, role, null);
    }

    public Result<User> execute(String orgId, String username, String password, String role, String zoneId) {
        if (username == null || username.trim().isEmpty()) {
            return Result.failure("Username is required");
        }
        if (!PasswordHasher.isValid(password)) {
            return Result.failure("Password must be at least 12 characters");
        }
        if (role == null || (!role.equals("ADMIN") && !role.equals("DISPATCHER")
                && !role.equals("WORKER") && !role.equals("COMPLIANCE_REVIEWER"))) {
            return Result.failure("Invalid role");
        }

        // Check if username already taken
        if (userRepository.findByUsername(username.trim()) != null) {
            return Result.failure("Username already taken");
        }

        String id = UUID.randomUUID().toString();
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash(password, salt);

        userRepository.insertUser(id, orgId, username.trim(), hash, salt, role);

        // Auto-create Worker profile for WORKER role users
        if ("WORKER".equals(role) && workerRepository != null) {
            Worker worker = new Worker(
                    UUID.randomUUID().toString(),
                    id,       // userId
                    orgId,
                    username.trim(),  // use username as worker display name
                    "AVAILABLE",
                    0,        // currentWorkload
                    3.0,      // default reputationScore
                    zoneId    // zoneId from admin selection (may be null)
            );
            workerRepository.insert(worker);
        }

        User user = new User(id, orgId, username.trim(), role, true);
        return Result.success(user);
    }
}
