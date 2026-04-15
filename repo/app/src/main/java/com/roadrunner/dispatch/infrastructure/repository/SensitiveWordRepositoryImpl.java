package com.roadrunner.dispatch.infrastructure.repository;

import com.roadrunner.dispatch.core.domain.repository.SensitiveWordRepository;
import com.roadrunner.dispatch.infrastructure.db.dao.SensitiveWordDao;
import com.roadrunner.dispatch.infrastructure.db.entity.SensitiveWordEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SensitiveWordRepositoryImpl implements SensitiveWordRepository {

    private final SensitiveWordDao sensitiveWordDao;

    public SensitiveWordRepositoryImpl(SensitiveWordDao sensitiveWordDao) {
        this.sensitiveWordDao = sensitiveWordDao;
    }

    @Override
    public List<String> getAllWords() {
        List<SensitiveWordEntity> entities = sensitiveWordDao.getAllWords();
        List<String> words = new ArrayList<>();
        if (entities != null) {
            for (SensitiveWordEntity e : entities) {
                words.add(e.word);
            }
        }
        return words;
    }

    @Override
    public List<String> getZeroToleranceWords() {
        List<SensitiveWordEntity> entities = sensitiveWordDao.getZeroToleranceWords();
        List<String> words = new ArrayList<>();
        if (entities != null) {
            for (SensitiveWordEntity e : entities) {
                words.add(e.word);
            }
        }
        return words;
    }

    @Override
    public void addWord(String word, boolean isZeroTolerance) {
        SensitiveWordEntity entity = new SensitiveWordEntity(
            UUID.randomUUID().toString(),
            word.toLowerCase().trim(),
            isZeroTolerance,
            System.currentTimeMillis()
        );
        sensitiveWordDao.insert(entity);
    }

    @Override
    public void removeWord(String id) {
        sensitiveWordDao.deleteById(id);
    }
}
