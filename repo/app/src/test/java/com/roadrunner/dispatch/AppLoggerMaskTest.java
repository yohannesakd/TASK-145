package com.roadrunner.dispatch;

import com.roadrunner.dispatch.core.util.AppLogger;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link AppLogger#mask(String)} — verifies that IDs are properly
 * truncated for safe logging, preventing sensitive identifier leakage.
 */
public class AppLoggerMaskTest {

    @Test
    public void mask_normalId_showsFirst4CharsPlusDots() {
        assertEquals("abcd...", AppLogger.mask("abcdefgh"));
    }

    @Test
    public void mask_uuid_showsFirst4CharsPlusDots() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String masked = AppLogger.mask(uuid);
        assertEquals("550e...", masked);
        assertFalse("Full UUID must not appear", masked.contains("446655440000"));
    }

    @Test
    public void mask_exactly5chars_showsFirst4PlusDots() {
        assertEquals("abcd...", AppLogger.mask("abcde"));
    }

    @Test
    public void mask_exactly4chars_returnsStars() {
        assertEquals("***", AppLogger.mask("abcd"));
    }

    @Test
    public void mask_shortId_returnsStars() {
        assertEquals("***", AppLogger.mask("ab"));
    }

    @Test
    public void mask_singleChar_returnsStars() {
        assertEquals("***", AppLogger.mask("x"));
    }

    @Test
    public void mask_emptyString_returnsStars() {
        assertEquals("***", AppLogger.mask(""));
    }

    @Test
    public void mask_null_returnsNullString() {
        assertEquals("null", AppLogger.mask(null));
    }

    @Test
    public void mask_neverLeaksFullOrgId() {
        String orgId = "org-company-12345";
        String masked = AppLogger.mask(orgId);
        assertFalse(masked.contains("company"));
        assertFalse(masked.contains("12345"));
    }

    @Test
    public void mask_neverLeaksFullUserId() {
        String userId = "user-john-doe-99";
        String masked = AppLogger.mask(userId);
        assertFalse(masked.contains("john"));
        assertFalse(masked.contains("doe"));
    }
}
