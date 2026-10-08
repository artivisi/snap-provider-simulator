package com.artivisi.snapsimulator.config;

import com.artivisi.snapsimulator.snap.PemKeys;
import com.artivisi.snapsimulator.snap.SnapSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankKeysTest {

    @Test
    @DisplayName("Loads a PKCS#8 key and derives the matching public key")
    void loadsAndDerives() {
        BankKeys keys = BankKeys.load(Path.of("src/test/resources/keys/bank-test-private.pem"));
        String signature = SnapSignature.signRsa(keys.privateKey(), "a|b");
        assertThat(SnapSignature.verifyRsa(PemKeys.parsePublicKey(keys.publicKeyPem()), "a|b", signature)).isTrue();
    }

    @Test
    @DisplayName("Missing file, wrong format and short key stop startup with the path in the message")
    void failsWithPath(@TempDir Path dir) throws Exception {
        Path missing = dir.resolve("missing.pem");
        assertThatThrownBy(() -> BankKeys.load(missing)).hasMessageContaining(missing.toString());

        Path pkcs1 = dir.resolve("pkcs1.pem");
        Files.writeString(pkcs1, "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----\n");
        assertThatThrownBy(() -> BankKeys.load(pkcs1)).hasMessageContaining(pkcs1.toString()).hasMessageContaining("pkcs8");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        Path shortKey = dir.resolve("short.pem");
        Files.writeString(shortKey, PemKeys.toPem(generator.generateKeyPair().getPrivate()));
        assertThatThrownBy(() -> BankKeys.load(shortKey)).hasMessageContaining("1024 bits");
    }
}
