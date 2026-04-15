package com.roadrunner.dispatch.infrastructure.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.roadrunner.dispatch.core.domain.model.User;
import com.roadrunner.dispatch.core.domain.model.UserAuthInfo;
import com.roadrunner.dispatch.core.domain.repository.UserRepository;
import com.roadrunner.dispatch.infrastructure.db.dao.UserDao;
import com.roadrunner.dispatch.infrastructure.db.entity.UserEntity;

import java.util.ArrayList;
import java.util.List;

public class UserRepositoryImpl implements UserRepository {
    private final UserDao userDao;

    public UserRepositoryImpl(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public User findByUsername(String username) {
        UserEntity entity = userDao.findByUsername(username);
        if (entity == null) return null;
        return new User(entity.id, entity.orgId, entity.username, entity.role, entity.isActive);
    }

    @Override
    public User findById(String id) {
        UserEntity entity = userDao.findById(id);
        if (entity == null) return null;
        return new User(entity.id, entity.orgId, entity.username, entity.role, entity.isActive);
    }

    @Override
    public UserAuthInfo getAuthInfo(String username) {
        UserEntity entity = userDao.findByUsername(username);
        if (entity == null) return null;
        return new UserAuthInfo(
            entity.id,
            entity.orgId,
            entity.username,
            entity.role,
            entity.isActive,
            entity.passwordHash,
            entity.passwordSalt,
            entity.failedAttempts,
            entity.lockedUntil
        );
    }

    @Override
    public void recordFailedAttempt(String userId, int newFailedCount, long lockedUntil) {
        userDao.updateLockState(userId, newFailedCount, lockedUntil);
    }

    @Override
    public void resetFailedAttempts(String userId) {
        userDao.updateLockState(userId, 0, 0);
    }

    @Override
    public void insertUser(String id, String orgId, String username,
                           String passwordHash, String passwordSalt, String role) {
        long now = System.currentTimeMillis();
        UserEntity entity = new UserEntity(
            id, orgId, username, passwordHash, passwordSalt, role,
            true, 0, 0, now, now
        );
        userDao.insertUser(entity);
    }

    @Override
    public LiveData<List<User>> getUsersByOrg(String orgId) {
        return Transformations.map(userDao.getUsersByOrg(orgId), entities -> {
            List<User> users = new ArrayList<>();
            if (entities != null) {
                for (UserEntity e : entities) {
                    users.add(new User(e.id, e.orgId, e.username, e.role, e.isActive));
                }
            }
            return users;
        });
    }
}
