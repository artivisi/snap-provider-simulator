package com.artivisi.snapsimulator.config;

import com.artivisi.snapsimulator.enums.Bank;
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
    @DisplayName("Loads one key per bank and derives each public key; the banks' keys differ")
    void loadsPerBank() {
        BankKeys keys = BankKeys.load(Path.of("src/test/resources/keys"));
        for (Bank bank : Bank.values()) {
            BankKey key = keys.of(bank);
            String signature = SnapSignature.signRsa(key.privateKey(), "a|b");
            assertThat(SnapSignature.verifyRsa(PemKeys.parsePublicKey(key.publicKeyPem()), "a|b", signature)).isTrue();
        }
        assertThat(keys.of(Bank.BRI).publicKeyPem()).isNotEqualTo(keys.of(Bank.BCA).publicKeyPem());
    }

    @Test
    @DisplayName("A missing bank key stops startup naming the file")
    void missingBank(@TempDir Path dir) throws Exception {
        Files.copy(Path.of("src/test/resources/keys/bri-private.pem"), dir.resolve("bri-private.pem"));
        assertThatThrownBy(() -> BankKeys.load(dir)).hasMessageContaining(dir.resolve("bca-private.pem").toString());
    }

    @Test
    @DisplayName("Missing file, wrong format and short key stop startup with the path in the message")
    void failsWithPath(@TempDir Path dir) throws Exception {
        Path missing = dir.resolve("missing.pem");
        assertThatThrownBy(() -> BankKey.load(missing)).hasMessageContaining(missing.toString());

        Path pkcs1 = dir.resolve("pkcs1.pem");
        Files.writeString(pkcs1, "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----\n");
        assertThatThrownBy(() -> BankKey.load(pkcs1)).hasMessageContaining(pkcs1.toString()).hasMessageContaining("pkcs8");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        Path shortKey = dir.resolve("short.pem");
        Files.writeString(shortKey, PemKeys.toPem(generator.generateKeyPair().getPrivate()));
        assertThatThrownBy(() -> BankKey.load(shortKey)).hasMessageContaining("1024 bits");
    }
}
