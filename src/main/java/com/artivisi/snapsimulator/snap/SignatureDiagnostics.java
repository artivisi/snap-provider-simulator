package com.artivisi.snapsimulator.snap;

import com.artivisi.snapsimulator.SpecRef;

import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Explains a failed signature check from the inputs the simulator used. The
 * output never contains a signature, only the strings that were signed and a
 * hint found by trying the common client mistakes.
 */
@SpecRef("sim.diagnostic-mode")
public final class SignatureDiagnostics {

    private SignatureDiagnostics() {
    }

    public static Map<String, String> asymmetric(String clientId, String timestamp, String receivedSignature,
            PublicKey registeredKey) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("expectedStringToSign", SnapSignature.asymmetricStringToSign(clientId, timestamp));
        d.put("receivedTimestamp", timestamp);
        if (registeredKey == null) {
            d.put("hint", "no public key registered for this client id: register one on the portal key page");
        } else {
            d.put("registeredKeySha256", SnapSignature.sha256Hex(PemKeys.toPem(registeredKey)));
            d.put("hint", SnapSignature.looksHex(receivedSignature)
                    ? "X-SIGNATURE looks hex; SNAP expects Base64 of the signature bytes"
                    : "signature does not verify with the registered public key: check that the private key matches "
                            + "it and that the string-to-sign is X-CLIENT-KEY|X-TIMESTAMP exactly as sent");
        }
        return d;
    }

    public static Map<String, String> symmetric(String method, String path, String query, String accessToken,
            String rawBody, String timestamp, String clientSecret, String receivedSignature) {
        String expected = SnapSignature.symmetricStringToSign(method, path, accessToken, rawBody, timestamp);
        Map<String, String> d = new LinkedHashMap<>();
        d.put("expectedStringToSign", expected);
        d.put("receivedTimestamp", timestamp);
        d.put("minifiedBody", JsonMinifier.minify(rawBody));
        d.put("bodySha256", SnapSignature.bodyHash(rawBody));
        d.put("rawBodySha256", SnapSignature.sha256Hex(rawBody));
        d.put("hint", symmetricHint(method, path, query, accessToken, rawBody, timestamp, clientSecret, receivedSignature));
        return d;
    }

    private static String symmetricHint(String method, String path, String query, String accessToken, String rawBody,
            String timestamp, String clientSecret, String received) {
        if (SnapSignature.looksHex(received)) {
            return "X-SIGNATURE looks hex; SNAP expects Base64 of the HMAC bytes";
        }
        String bodyHash = SnapSignature.bodyHash(rawBody);
        if (matches(clientSecret, received, method + ":" + path + ":" + accessToken + ":"
                + SnapSignature.sha256Hex(rawBody) + ":" + timestamp)) {
            return "raw body hash matches but minified does not: the client did not minify the body before hashing";
        }
        if (matches(clientSecret, received, method + ":" + path + ":" + accessToken + ":"
                + bodyHash.toUpperCase(Locale.ROOT) + ":" + timestamp)) {
            return "body hash must be lowercase hex";
        }
        if (query != null && matches(clientSecret, received, method + ":" + path + "?" + query + ":" + accessToken
                + ":" + bodyHash + ":" + timestamp)) {
            return "the path in the string-to-sign must not include the query string";
        }
        if (matches(clientSecret, received, method + ":" + path + ":Bearer " + accessToken + ":" + bodyHash + ":"
                + timestamp)) {
            return "the access token in the string-to-sign must not include the \"Bearer \" prefix";
        }
        return "no common mistake matched: check the client secret, the access token, the HTTP method and that the "
                + "signed timestamp equals X-TIMESTAMP";
    }

    private static boolean matches(String secret, String received, String candidate) {
        return SnapSignature.verifyHmac(secret, candidate, received);
    }
}
