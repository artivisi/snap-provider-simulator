package com.artivisi.snapsimulator.snap;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** RSA keys in PEM: X.509 SubjectPublicKeyInfo and PKCS#8, JDK only. */
public final class PemKeys {

    public static final int MIN_RSA_BITS = 2048;

    private static final String PUBLIC = "PUBLIC KEY";
    private static final String PRIVATE = "PRIVATE KEY";
    private static final String RSA_PRIVATE = "RSA PRIVATE KEY";

    private PemKeys() {
    }

    /** @throws InvalidKeyException with a message for the partner */
    public static RSAPublicKey parsePublicKey(String pem) {
        if (pem.contains("PRIVATE KEY-----")) {
            throw new InvalidKeyException("This is a private key. Upload the public key only; the private key stays with you.");
        }
        byte[] der = decode(pem, PUBLIC, "Expected a PEM block starting with -----BEGIN PUBLIC KEY-----.");
        try {
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            if (key.getModulus().bitLength() < MIN_RSA_BITS) {
                throw new InvalidKeyException("RSA key is " + key.getModulus().bitLength() + " bits; at least "
                        + MIN_RSA_BITS + " required.");
            }
            return key;
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new InvalidKeyException("Not an RSA public key (X.509 SubjectPublicKeyInfo).", e);
        }
    }

    /** @throws InvalidKeyException with a message for the operator or partner */
    public static PrivateKey parsePrivateKey(String pem) {
        if (pem.contains("-----BEGIN " + RSA_PRIVATE + "-----")) {
            throw new InvalidKeyException("PKCS#1 key (BEGIN RSA PRIVATE KEY). Convert with: "
                    + "openssl pkcs8 -topk8 -nocrypt -in old.pem -out private.pem");
        }
        byte[] der = decode(pem, PRIVATE, "Expected a PEM block starting with -----BEGIN PRIVATE KEY-----.");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new InvalidKeyException("Not an RSA private key (PKCS#8).", e);
        }
    }

    public static KeyPair generateRsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MIN_RSA_BITS);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA key generation failed", e);
        }
    }

    public static String toPem(java.security.PublicKey key) {
        return encode(PUBLIC, key.getEncoded());
    }

    public static String toPem(PrivateKey key) {
        return encode(PRIVATE, key.getEncoded());
    }

    private static String encode(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    private static byte[] decode(String pem, String type, String missingMessage) {
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int start = pem.indexOf(begin);
        int stop = pem.indexOf(end);
        if (start < 0 || stop < start) {
            throw new InvalidKeyException(missingMessage);
        }
        String base64 = pem.substring(start + begin.length(), stop).replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new InvalidKeyException("PEM body is not valid Base64.", e);
        }
    }

    public static class InvalidKeyException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public InvalidKeyException(String message) {
            super(message);
        }

        public InvalidKeyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
