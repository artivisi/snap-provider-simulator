package com.artivisi.snapsimulator.config;

import com.artivisi.snapsimulator.snap.PemKeys;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;

/** The simulator's bank identity: one RSA key pair, loaded from the configured PKCS#8 file. */
public record BankKeys(PrivateKey privateKey, PublicKey publicKey) {

    public static BankKeys load(Path path) {
        String pem;
        try {
            pem = Files.readString(path, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bank private key at " + path + ": " + e.getMessage(), e);
        }
        PrivateKey privateKey;
        try {
            privateKey = PemKeys.parsePrivateKey(pem);
        } catch (PemKeys.InvalidKeyException e) {
            throw new IllegalStateException("Bank private key at " + path + " is unusable: " + e.getMessage(), e);
        }
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalStateException("Bank private key at " + path + " has no CRT parameters; cannot derive the public key");
        }
        if (crt.getModulus().bitLength() < PemKeys.MIN_RSA_BITS) {
            throw new IllegalStateException("Bank private key at " + path + " is " + crt.getModulus().bitLength()
                    + " bits; at least " + PemKeys.MIN_RSA_BITS + " required");
        }
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            return new BankKeys(privateKey, publicKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot derive bank public key from " + path, e);
        }
    }

    public String publicKeyPem() {
        return PemKeys.toPem(publicKey);
    }
}
