package com.artivisi.snapsimulator.util;

import java.security.SecureRandom;

public final class Randoms {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String DIGITS = "0123456789";

    private Randoms() {
    }

    public static String alphanumeric(int length) {
        return pick(ALPHANUMERIC, length);
    }

    public static String digits(int length) {
        return pick(DIGITS, length);
    }

    private static String pick(String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
