package com.artivisi.snapsimulator.util;

/** Strips control characters and bounds length of user-controlled values before logging. */
public final class LogSanitizer {

    private static final int MAX_LENGTH = 200;

    private LogSanitizer() {
    }

    public static String sanitize(String input) {
        if (input == null) {
            return "null";
        }
        String clean = input.replaceAll("[\\x00-\\x1f\\x7f]", "_");
        return clean.length() > MAX_LENGTH ? clean.substring(0, MAX_LENGTH) + "...[truncated]" : clean;
    }
}
