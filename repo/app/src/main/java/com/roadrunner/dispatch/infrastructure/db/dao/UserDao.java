package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.roadrunner.dispatch.infrastructure.db.entity.UserEntity;

import java.util.List;

@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertUser(UserEntity user);

    @Update
    void updateUser(UserEntity user);

    @Nullable
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    UserEntity findByUsername(String username);

    @Nullable
    @Query("SELECT * FROM users WHERE id = :id")
    UserEntity findById(String id);

    @Query("SELECT * FROM users WHERE org_id = :orgId")
    LiveData<List<UserEntity>> getUsersByOrg(String orgId);

    @Query("UPDATE users SET failed_attempts = :count, locked_until = :lockedUntil WHERE id = :id")
    void updateLockState(String id, int count, long lockedUntil);
}
