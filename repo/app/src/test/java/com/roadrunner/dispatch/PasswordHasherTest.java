package com.roadrunner.dispatch;

import com.roadrunner.dispatch.core.util.PasswordHasher;

import org.junit.Test;

import static org.junit.Assert.*;

public class PasswordHasherTest {

    // -----------------------------------------------------------------------
    // Hash determinism
    // -----------------------------------------------------------------------

    @Test
    public void sameSalt_sameHash() {
        String salt = PasswordHasher.generateSalt();
        String hash1 = PasswordHasher.hash("myPassword123!", salt);
        String hash2 = PasswordHasher.hash("myPassword123!", salt);
        assertEquals("Same password + same salt must produce same hash", hash1, hash2);
    }

    @Test
    public void differentSalt_differentHash() {
        String salt1 = PasswordHasher.generateSalt();
        String salt2 = PasswordHasher.generateSalt();
        String hash1 = PasswordHasher.hash("myPassword123!", salt1);
        String hash2 = PasswordHasher.hash("myPassword123!", salt2);
        assertNotEquals("Different salts must produce different hashes", hash1, hash2);
    }

    @Test
    public void differentPassword_sameHash_different() {
        String salt = PasswordHasher.generateSalt();
        String hash1 = PasswordHasher.hash("password1234567", salt);
        String hash2 = PasswordHasher.hash("password9999999", salt);
        assertNotEquals(hash1, hash2);
    }

    // -----------------------------------------------------------------------
    // Verify
    // -----------------------------------------------------------------------

    @Test
    public void verify_correctPassword_returnsTrue() {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash("correctPass123!", salt);
        assertTrue(PasswordHasher.verify("correctPass123!", salt, hash));
    }

    @Test
    public void verify_wrongPassword_returnsFalse() {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash("correctPass123!", salt);
        assertFalse(PasswordHasher.verify("wrongPassword!", salt, hash));
    }

    @Test
    public void verify_emptyPassword_returnsFalse() {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash("correctPass123!", salt);
        assertFalse(PasswordHasher.verify("", salt, hash));
    }

    @Test
    public void verify_caseSensitive_differentCaseReturnsFalse() {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash("Password123!", salt);
        assertFalse("Password hashing must be case-sensitive",
                PasswordHasher.verify("password123!", salt, hash));
    }

    // -----------------------------------------------------------------------
    // isValid
    // -----------------------------------------------------------------------

    @Test
    public void isValid_null_returnsFalse() {
        assertFalse(PasswordHasher.isValid(null));
    }

    @Test
    public void isValid_empty_returnsFalse() {
        assertFalse(PasswordHasher.isValid(""));
    }

    @Test
    public void isValid_elevenChars_returnsFalse() {
        // 11 characters — one short of minimum
        assertFalse(PasswordHasher.isValid("abcdefghijk")); // length = 11
    }

    @Test
    public void isValid_twelveChars_returnsTrue() {
        // Exactly 12 characters — minimum boundary
        assertTrue(PasswordHasher.isValid("abcdefghijkl")); // length = 12
    }

    @Test
    public void isValid_thirteenChars_returnsTrue() {
        assertTrue(PasswordHasher.isValid("abcdefghijklm")); // length = 13
    }

    @Test
    public void isValid_longPassword_returnsTrue() {
        assertTrue(PasswordHasher.isValid("This_Is_A_Very_Long_And_Secure_Password123!"));
    }

    // -----------------------------------------------------------------------
    // generateSalt
    // -----------------------------------------------------------------------

    @Test
    public void generateSalt_producesNonNullNonEmpty() {
        String salt = PasswordHasher.generateSalt();
        assertNotNull(salt);
        assertFalse(salt.isEmpty());
    }

    @Test
    public void generateSalt_eachCallProducesDifferentSalt() {
        String s1 = PasswordHasher.generateSalt();
        String s2 = PasswordHasher.generateSalt();
        assertNotEquals("Each salt should be unique", s1, s2);
    }

    @Test
    public void generateSalt_isValidBase64() {
        String salt = PasswordHasher.generateSalt();
        // Should decode without exception
        try {
            java.util.Base64.getDecoder().decode(salt);
        } catch (IllegalArgumentException e) {
            fail("Salt should be valid Base64");
        }
    }
}
