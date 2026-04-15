package com.roadrunner.dispatch.core.domain.repository;

import androidx.lifecycle.LiveData;

import com.roadrunner.dispatch.core.domain.model.User;
import com.roadrunner.dispatch.core.domain.model.UserAuthInfo;

import java.util.List;

public interface UserRepository {
    User findByUsername(String username);
    User findById(String id);
    UserAuthInfo getAuthInfo(String username);
    void recordFailedAttempt(String userId, int newFailedCount, long lockedUntil);
    void resetFailedAttempts(String userId);
    void insertUser(String id, String orgId, String username, String passwordHash, String passwordSalt, String role);
    LiveData<List<User>> getUsersByOrg(String orgId);
}
