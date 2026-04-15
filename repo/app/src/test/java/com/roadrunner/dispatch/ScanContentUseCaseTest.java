package com.roadrunner.dispatch;

import com.roadrunner.dispatch.core.domain.model.ContentScanResult;
import com.roadrunner.dispatch.core.domain.repository.SensitiveWordRepository;
import com.roadrunner.dispatch.core.domain.usecase.ScanContentUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ScanContentUseCaseTest {

    private StubSensitiveWordRepository wordRepo;
    private ScanContentUseCase useCase;

    @Before
    public void setUp() {
        wordRepo = new StubSensitiveWordRepository();
        useCase = new ScanContentUseCase(wordRepo);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void cleanText_noSensitiveWords_statusClean() {
        wordRepo.addWord("badword", false);
        wordRepo.addWord("threat", true);

        ContentScanResult result = useCase.execute("This is a perfectly fine message.");
        assertEquals("CLEAN", result.status);
        assertTrue(result.matchedTerms.isEmpty());
        assertFalse(result.isZeroTolerance);
    }

    @Test
    public void textWithSensitiveWord_flagged() {
        wordRepo.addWord("badword", false);

        ContentScanResult result = useCase.execute("This contains a badword in it.");
        assertEquals("FLAGGED", result.status);
        assertTrue(result.matchedTerms.contains("badword"));
        assertFalse(result.isZeroTolerance);
    }

    @Test
    public void textWithZeroToleranceWord_zeroTolerance() {
        wordRepo.addWord("threat", true);

        ContentScanResult result = useCase.execute("I will make a threat against you.");
        assertEquals("ZERO_TOLERANCE", result.status);
        assertTrue(result.isZeroTolerance);
        assertTrue(result.matchedTerms.contains("threat"));
    }

    @Test
    public void caseInsensitive_THREAT_matchesThreat() {
        wordRepo.addWord("threat", true);

        ContentScanResult result = useCase.execute("I will make a THREAT against you.");
        assertEquals("ZERO_TOLERANCE", result.status);
        assertTrue(result.isZeroTolerance);
    }

    @Test
    public void caseInsensitive_mixedCase_matchesSensitiveWord() {
        wordRepo.addWord("badword", false);

        ContentScanResult result = useCase.execute("He said BadWord out loud.");
        assertEquals("FLAGGED", result.status);
    }

    @Test
    public void multipleMatches_allListed() {
        wordRepo.addWord("hate", false);
        wordRepo.addWord("abuse", false);

        ContentScanResult result = useCase.execute("This contains hate and abuse in it.");
        assertEquals("FLAGGED", result.status);
        assertTrue("Should contain 'hate'", result.matchedTerms.contains("hate"));
        assertTrue("Should contain 'abuse'", result.matchedTerms.contains("abuse"));
        assertEquals(2, result.matchedTerms.size());
    }

    @Test
    public void partialWordMatch_threatening_doesNotMatchThreat() {
        // "threatening" should NOT match "threat" due to whole-word boundary requirement
        wordRepo.addWord("threat", true);

        ContentScanResult result = useCase.execute("The email had a threatening tone.");
        // "threatening" is not a whole-word match for "threat"
        assertEquals("CLEAN", result.status);
        assertFalse(result.isZeroTolerance);
    }

    @Test
    public void partialWord_badwords_doesNotMatchBadword() {
        wordRepo.addWord("badword", false);

        ContentScanResult result = useCase.execute("He used many badwords.");
        // "badwords" is not a whole-word match for "badword"
        assertEquals("CLEAN", result.status);
    }

    @Test
    public void emptyText_clean() {
        wordRepo.addWord("threat", true);
        wordRepo.addWord("badword", false);

        ContentScanResult result = useCase.execute("");
        assertEquals("CLEAN", result.status);
        assertTrue(result.matchedTerms.isEmpty());
    }

    @Test
    public void whitespaceOnlyText_clean() {
        wordRepo.addWord("threat", true);

        ContentScanResult result = useCase.execute("   ");
        assertEquals("CLEAN", result.status);
    }

    @Test
    public void nullText_clean() {
        wordRepo.addWord("threat", true);

        ContentScanResult result = useCase.execute(null);
        assertEquals("CLEAN", result.status);
        assertFalse(result.isZeroTolerance);
    }

    @Test
    public void mixedZeroToleranceAndNormal_zeroToleranceTakesPriority() {
        wordRepo.addWord("threat", true);
        wordRepo.addWord("hate", false);

        ContentScanResult result = useCase.execute("This has hate and a direct threat.");
        // Zero-tolerance takes priority
        assertEquals("ZERO_TOLERANCE", result.status);
        assertTrue(result.isZeroTolerance);
        assertTrue(result.matchedTerms.contains("threat"));
        assertTrue(result.matchedTerms.contains("hate"));
    }

    @Test
    public void wordAtStartOfString_detected() {
        wordRepo.addWord("hate", false);
        ContentScanResult result = useCase.execute("hate is wrong.");
        assertEquals("FLAGGED", result.status);
    }

    @Test
    public void wordAtEndOfString_detected() {
        wordRepo.addWord("hate", false);
        ContentScanResult result = useCase.execute("I feel hate");
        assertEquals("FLAGGED", result.status);
    }

    @Test
    public void noWordsConfigured_alwaysClean() {
        // Empty word lists
        ContentScanResult result = useCase.execute("This contains any words you want.");
        assertEquals("CLEAN", result.status);
    }

    // -----------------------------------------------------------------------
    // Stub implementation
    // -----------------------------------------------------------------------

    private static class StubSensitiveWordRepository implements SensitiveWordRepository {
        private final List<String> allWords = new ArrayList<>();
        private final List<String> zeroToleranceWords = new ArrayList<>();

        @Override
        public List<String> getAllWords() { return new ArrayList<>(allWords); }

        @Override
        public List<String> getZeroToleranceWords() { return new ArrayList<>(zeroToleranceWords); }

        @Override
        public void addWord(String word, boolean isZeroTolerance) {
            allWords.add(word);
            if (isZeroTolerance) zeroToleranceWords.add(word);
        }

        @Override
        public void removeWord(String id) {
            allWords.remove(id);
            zeroToleranceWords.remove(id);
        }
    }
}
