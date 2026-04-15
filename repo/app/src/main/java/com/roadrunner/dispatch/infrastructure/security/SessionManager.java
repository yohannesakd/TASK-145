package com.roadrunner.dispatch.infrastructure.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.roadrunner.dispatch.core.util.AppLogger;

public class SessionManager {
    private static final String PREFS_NAME = "roadrunner_secure_prefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ORG_ID = "org_id";
    private static final String KEY_ROLE = "role";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_SESSION_CREATED = "session_created";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            this.prefs = EncryptedSharedPreferences.create(
                PREFS_NAME, masterKeyAlias,
                context.getApplicationContext(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            AppLogger.error("Security", "FATAL: Cannot create encrypted storage", e);
            throw new RuntimeException("Encrypted storage unavailable — cannot proceed safely", e);
        }
    }

    public void createSession(String userId, String orgId, String role, String username) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ORG_ID, orgId)
            .putString(KEY_ROLE, role)
            .putString(KEY_USERNAME, username)
            .putLong(KEY_SESSION_CREATED, System.currentTimeMillis())
            .apply();
        AppLogger.info("Auth", "Session created for role=" + role + " org=" + AppLogger.mask(orgId));
    }

    public void clearSession() {
        prefs.edit().clear().apply();
        AppLogger.info("Auth", "Session cleared");
    }

    public boolean isLoggedIn() {
        return prefs.getString(KEY_USER_ID, null) != null;
    }

    public String getUserId() { return prefs.getString(KEY_USER_ID, null); }
    public String getOrgId() { return prefs.getString(KEY_ORG_ID, null); }
    public String getRole() { return prefs.getString(KEY_ROLE, null); }
    public String getUsername() { return prefs.getString(KEY_USERNAME, null); }
    public long getSessionCreatedAt() { return prefs.getLong(KEY_SESSION_CREATED, 0); }
}
