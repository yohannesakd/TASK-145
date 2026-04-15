package com.roadrunner.dispatch.core.util;

import android.util.Log;

/**
 * Centralised logging wrapper around Android's {@link Log}.
 *
 * <p>All application logging <b>must</b> go through this class — direct use of
 * {@code android.util.Log} is prohibited. Debug and info messages are suppressed
 * in release builds via {@link Log#isLoggable} checks. Identifiers are masked via
 * {@link #mask(String)} to prevent PII leakage.
 */
public final class AppLogger {
    private static final String PREFIX = "RR";

    private AppLogger() {}

    public static void debug(String module, String message) {
        String tag = PREFIX + ":" + module;
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }

    public static void info(String module, String message) {
        String tag = PREFIX + ":" + module;
        if (Log.isLoggable(tag, Log.INFO)) {
            Log.i(tag, message);
        }
    }

    public static void warn(String module, String message) {
        Log.w(PREFIX + ":" + module, message);
    }

    public static void error(String module, String message) {
        Log.e(PREFIX + ":" + module, message);
    }

    public static void error(String module, String message, Throwable t) {
        Log.e(PREFIX + ":" + module, message, t);
    }

    /** Mask an identifier for safe logging: shows first 4 chars + "..." */
    public static String mask(String id) {
        if (id == null) return "null";
        if (id.length() <= 4) return "***";
        return id.substring(0, 4) + "...";
    }
}
