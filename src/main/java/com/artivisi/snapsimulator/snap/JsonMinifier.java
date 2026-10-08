package com.artivisi.snapsimulator.snap;

import com.artivisi.snapsimulator.SpecRef;

/**
 * Removes whitespace outside JSON strings. Works on tokens, never re-renders
 * values, so numbers and escapes keep the exact form the sender used.
 */
@SpecRef("snap.sig.body-hash")
public final class JsonMinifier {

    private JsonMinifier() {
    }

    public static String minify(String json) {
        StringBuilder out = new StringBuilder(json.length());
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 >= json.length()) {
                        throw new IllegalArgumentException("JSON ends inside an escape sequence");
                    }
                    out.append(json.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
                out.append(c);
            } else if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                out.append(c);
            }
        }
        if (inString) {
            throw new IllegalArgumentException("JSON ends inside a string");
        }
        return out.toString();
    }
}
