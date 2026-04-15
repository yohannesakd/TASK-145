package com.roadrunner.dispatch.core.domain.repository;

import java.util.List;

public interface SensitiveWordRepository {
    List<String> getAllWords();
    List<String> getZeroToleranceWords();
    void addWord(String word, boolean isZeroTolerance);
    void removeWord(String id);
}
