package com.artivisi.snapsimulator.snap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PemKeysTest {

    @Test
    @DisplayName("Generated pair round-trips through PEM and still signs/verifies")
    void roundTrip() {
        KeyPair pair = PemKeys.generateRsa();
        String publicPem = PemKeys.toPem(pair.getPublic());
        String privatePem = PemKeys.toPem(pair.getPrivate());
        assertThat(publicPem).startsWith("-----BEGIN PUBLIC KEY-----\n").endsWith("-----END PUBLIC KEY-----\n");
        assertThat(privatePem).startsWith("-----BEGIN PRIVATE KEY-----\n");
        RSAPublicKey parsed = PemKeys.parsePublicKey(publicPem);
        assertThat(parsed.getModulus().bitLength()).isEqualTo(2048);
        String signature = SnapSignature.signRsa(PemKeys.parsePrivateKey(privatePem), "x|y");
        assertThat(SnapSignature.verifyRsa(parsed, "x|y", signature)).isTrue();
    }

    @Test
    @DisplayName("A private key pasted as public key is rejected with a message saying so")
    void rejectsPrivateAsPublic() {
        String privatePem = PemKeys.toPem(PemKeys.generateRsa().getPrivate());
        assertThatThrownBy(() -> PemKeys.parsePublicKey(privatePem))
                .isInstanceOf(PemKeys.InvalidKeyException.class).hasMessageContaining("private key");
    }

    @Test
    @DisplayName("Keys below 2048 bits are rejected")
    void rejectsShortKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        String pem = PemKeys.toPem(generator.generateKeyPair().getPublic());
        assertThatThrownBy(() -> PemKeys.parsePublicKey(pem)).hasMessageContaining("1024 bits");
    }

    @Test
    @DisplayName("Non-RSA keys, missing PEM armour and bad Base64 are rejected")
    void rejectsMalformed() throws Exception {
        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(256);
        String ecPem = PemKeys.toPem(ec.generateKeyPair().getPublic());
        assertThatThrownBy(() -> PemKeys.parsePublicKey(ecPem)).hasMessageContaining("Not an RSA public key");
        assertThatThrownBy(() -> PemKeys.parsePublicKey("hello")).hasMessageContaining("BEGIN PUBLIC KEY");
        assertThatThrownBy(() -> PemKeys.parsePublicKey("-----BEGIN PUBLIC KEY-----\n@@@\n-----END PUBLIC KEY-----"))
                .hasMessageContaining("Base64");
        assertThatThrownBy(() -> PemKeys.parsePrivateKey("-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}) + "\n-----END PRIVATE KEY-----"))
                .hasMessageContaining("Not an RSA private key");
    }

    @Test
    @DisplayName("PKCS#1 private keys get the openssl conversion command")
    void pkcs1Hint() {
        assertThatThrownBy(() -> PemKeys.parsePrivateKey("-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----"))
                .hasMessageContaining("openssl pkcs8 -topk8 -nocrypt");
    }
}
