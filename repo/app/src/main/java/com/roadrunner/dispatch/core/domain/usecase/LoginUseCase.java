package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Session;
import com.roadrunner.dispatch.core.domain.model.UserAuthInfo;
import com.roadrunner.dispatch.core.domain.repository.UserRepository;
import com.roadrunner.dispatch.core.util.PasswordHasher;

public class LoginUseCase {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L;

    private final UserRepository userRepository;

    public LoginUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Result<Session> execute(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            return Result.failure("Username is required");
        }
        if (password == null || password.isEmpty()) {
            return Result.failure("Password is required");
        }

        UserAuthInfo authInfo = userRepository.getAuthInfo(username.trim());
        if (authInfo == null) {
            // Don't reveal whether username exists
            return Result.failure("Invalid credentials");
        }

        if (!authInfo.isActive) {
            return Result.failure("Account is deactivated");
        }

        // Check lockout
        long now = System.currentTimeMillis();
        if (authInfo.lockedUntil > now) {
            long remainingMs = authInfo.lockedUntil - now;
            long remainingMin = (remainingMs / 60000) + 1;
            return Result.failure("Account locked. Try again in " + remainingMin + " minute(s).");
        }

        // Verify password
        if (!PasswordHasher.verify(password, authInfo.passwordSalt, authInfo.passwordHash)) {
            int newCount = authInfo.failedAttempts + 1;
            if (newCount >= MAX_FAILED_ATTEMPTS) {
                long lockUntil = now + LOCKOUT_DURATION_MS;
                userRepository.recordFailedAttempt(authInfo.userId, newCount, lockUntil);
                return Result.failure("Account locked for 15 minutes due to too many failed attempts.");
            } else {
                userRepository.recordFailedAttempt(authInfo.userId, newCount, 0);
                return Result.failure("Invalid credentials");
            }
        }

        // Success — reset failed attempts
        userRepository.resetFailedAttempts(authInfo.userId);

        Session session = new Session(
            authInfo.userId,
            authInfo.orgId,
            authInfo.role,
            now
        );
        return Result.success(session);
    }
}
