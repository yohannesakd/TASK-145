package com.roadrunner.dispatch.infrastructure.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.roadrunner.dispatch.infrastructure.db.entity.SensitiveWordEntity;

import java.util.List;

@Dao
public interface SensitiveWordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SensitiveWordEntity word);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SensitiveWordEntity> words);

    @Query("SELECT * FROM sensitive_words")
    List<SensitiveWordEntity> getAllWords();

    @Query("SELECT * FROM sensitive_words WHERE is_zero_tolerance = 1")
    List<SensitiveWordEntity> getZeroToleranceWords();

    @Query("DELETE FROM sensitive_words WHERE id = :id")
    void deleteById(String id);
}
