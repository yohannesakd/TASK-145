package com.roadrunner.dispatch.core.domain.usecase;

import com.roadrunner.dispatch.core.domain.model.ContentScanResult;
import com.roadrunner.dispatch.core.domain.repository.SensitiveWordRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanContentUseCase {
    private final SensitiveWordRepository sensitiveWordRepository;

    public ScanContentUseCase(SensitiveWordRepository sensitiveWordRepository) {
        this.sensitiveWordRepository = sensitiveWordRepository;
    }

    public ContentScanResult execute(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new ContentScanResult("CLEAN", new ArrayList<>(), false);
        }

        String lowerContent = content.toLowerCase();
        List<String> matchedTerms = new ArrayList<>();
        boolean hasZeroTolerance = false;

        // Check zero-tolerance words first
        List<String> zeroToleranceWords = sensitiveWordRepository.getZeroToleranceWords();
        for (String word : zeroToleranceWords) {
            if (matchesWholeWord(lowerContent, word.toLowerCase())) {
                matchedTerms.add(word);
                hasZeroTolerance = true;
            }
        }

        // Check all sensitive words
        List<String> allWords = sensitiveWordRepository.getAllWords();
        for (String word : allWords) {
            String lowerWord = word.toLowerCase();
            if (!matchedTerms.contains(word) && matchesWholeWord(lowerContent, lowerWord)) {
                matchedTerms.add(word);
            }
        }

        if (matchedTerms.isEmpty()) {
            return new ContentScanResult("CLEAN", matchedTerms, false);
        } else if (hasZeroTolerance) {
            return new ContentScanResult("ZERO_TOLERANCE", matchedTerms, true);
        } else {
            return new ContentScanResult("FLAGGED", matchedTerms, false);
        }
    }

    private boolean matchesWholeWord(String text, String word) {
        // Whole word boundary match
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find();
    }
}
