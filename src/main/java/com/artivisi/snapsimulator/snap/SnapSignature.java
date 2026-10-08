package com.artivisi.snapsimulator.snap;

import com.artivisi.snapsimulator.SpecRef;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;

/** SNAP signatures: Base64 output (A1), HMAC-SHA512 (A2), path without query (A3). */
public final class SnapSignature {

    private static final String RSA = "SHA256withRSA";
    private static final String HMAC = "HmacSHA512";

    private SnapSignature() {
    }

    @SpecRef("snap.sig.asymmetric-token")
    public static String asymmetricStringToSign(String clientId, String timestamp) {
        return clientId + "|" + timestamp;
    }

    @SpecRef("snap.sig.asymmetric-token")
    public static String signRsa(PrivateKey key, String stringToSign) {
        try {
            Signature signature = Signature.getInstance(RSA);
            signature.initSign(key);
            signature.update(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA signing failed", e);
        }
    }

    /** False for a wrong signature and for a value that is not Base64. */
    @SpecRef("snap.sig.asymmetric-token")
    public static boolean verifyRsa(PublicKey key, String stringToSign, String base64Signature) {
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(base64Signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance(RSA);
            signature.initVerify(key);
            signature.update(stringToSign.getBytes(StandardCharsets.UTF_8));
            return signature.verify(signatureBytes);
        } catch (java.security.SignatureException e) {
            return false;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA verification failed", e);
        }
    }

    /** Lowercase hex SHA-256 of the minified body; an empty body hashes as "" (A4). */
    @SpecRef("snap.sig.body-hash")
    public static String bodyHash(String body) {
        return sha256Hex(JsonMinifier.minify(body));
    }

    @SpecRef("snap.sig.body-hash")
    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @SpecRef("snap.sig.symmetric")
    public static String symmetricStringToSign(String method, String path, String accessToken, String body,
            String timestamp) {
        return method + ":" + path + ":" + accessToken + ":" + bodyHash(body) + ":" + timestamp;
    }

    @SpecRef("snap.sig.symmetric")
    public static String hmac(String clientSecret, String stringToSign) {
        return Base64.getEncoder().encodeToString(hmacBytes(clientSecret, stringToSign));
    }

    /** Constant-time comparison; false for a value that is not Base64. */
    @SpecRef("snap.sig.symmetric")
    public static boolean verifyHmac(String clientSecret, String stringToSign, String base64Signature) {
        byte[] received;
        try {
            received = Base64.getDecoder().decode(base64Signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(hmacBytes(clientSecret, stringToSign), received);
    }

    /** True when the value is hex rather than Base64 (a common client mistake, reported in diagnostic mode). */
    public static boolean looksHex(String signature) {
        return signature.length() % 2 == 0 && signature.matches("[0-9a-fA-F]+");
    }

    private static byte[] hmacBytes(String clientSecret, String stringToSign) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), HMAC));
            return mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA512 failed", e);
        }
    }
}
