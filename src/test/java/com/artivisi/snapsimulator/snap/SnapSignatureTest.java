package com.artivisi.snapsimulator.snap;

import com.artivisi.snapsimulator.SpecRef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;

import static org.assertj.core.api.Assertions.assertThat;

/** Expected values were produced with openssl from the same inputs. */
class SnapSignatureTest {

    private static final String TIMESTAMP = "2026-10-08T10:00:00.000+07:00";
    private static PrivateKey fixtureKey;

    @BeforeAll
    static void loadKey() throws Exception {
        fixtureKey = PemKeys.parsePrivateKey(Files.readString(Path.of("src/test/resources/keys/bank-test-private.pem")));
    }

    @Test
    @SpecRef("snap.sig.asymmetric-token")
    @DisplayName("RSA signature over clientId|timestamp matches openssl dgst -sha256 -sign, Base64")
    void rsaMatchesOpenssl() {
        String stringToSign = SnapSignature.asymmetricStringToSign("client-001", TIMESTAMP);
        assertThat(stringToSign).isEqualTo("client-001|" + TIMESTAMP);
        assertThat(SnapSignature.signRsa(fixtureKey, stringToSign)).isEqualTo(
                "EtATPdrF8YH1yqzADgnCvDDPYW95C6Vo4XtFpmUGxgpXT5KxgQrkmZBTR+Ar1pr7ewPRAwrl1NJwdvBjuRcLlxUcOUO5CPCBXhOP+csL3of7L7fQXPvmf/9xdx+/y+cqJBmeBhofCfvdCRf+0PyKBlGcY64s0huMWpPHFa0K9/CX5u7zD+d63SbX875kf+A2QRJYc90+pqAafggSCmb96x5NcN2nL6qL7YWkSI96xyvpMYWWEgwXNa+BVUf1EbsQcrzg6iVs9DV5fFvzo76mg/TTLmiwxF4ElP60xRfd3kKDioQx79xNdkFse2YZ71leM8kJ350qZB61l6iy9hhwxA==");
    }

    @Test
    @SpecRef("snap.sig.asymmetric-token")
    @DisplayName("RSA verify accepts the right key and rejects a wrong key, altered input and non-Base64")
    void rsaVerify() {
        KeyPair pair = PemKeys.generateRsa();
        KeyPair other = PemKeys.generateRsa();
        String signature = SnapSignature.signRsa(pair.getPrivate(), "c|t");
        assertThat(SnapSignature.verifyRsa(pair.getPublic(), "c|t", signature)).isTrue();
        assertThat(SnapSignature.verifyRsa(other.getPublic(), "c|t", signature)).isFalse();
        assertThat(SnapSignature.verifyRsa(pair.getPublic(), "c|u", signature)).isFalse();
        assertThat(SnapSignature.verifyRsa(pair.getPublic(), "c|t", "not base64!")).isFalse();
        assertThat(SnapSignature.verifyRsa(pair.getPublic(), "c|t", "AAAA")).isFalse();
    }

    @Test
    @SpecRef("snap.sig.body-hash")
    @DisplayName("Body hash: index check values and the empty body")
    void bodyHash() {
        assertThat(SnapSignature.bodyHash("{ \"hello\" : \"world\" }"))
                .isEqualTo("93a23971a914e5eacbf0a8d25154cda309c3c1c72fbb9914d47c60f3cb681588");
        assertThat(SnapSignature.bodyHash(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @SpecRef("snap.sig.symmetric")
    @DisplayName("HMAC-SHA512 over the symmetric string-to-sign matches openssl, Base64")
    void hmacMatchesOpenssl() {
        String stringToSign = SnapSignature.symmetricStringToSign("POST", "/snap/v1.0/transfer-va/create-va", "tok123",
                "{ \"a\": \"b c\",\n \"n\": 1.50 }", TIMESTAMP);
        assertThat(stringToSign).isEqualTo("POST:/snap/v1.0/transfer-va/create-va:tok123:"
                + "df3472b0c34bb2e31ebf6dfc47ba309515bb0cd58f12fb1feeb015c347a1c25b:" + TIMESTAMP);
        String expected = "pNiXQ03zk8m+/sXMoY3boLCIeOuSWyoVj+QAjTY9rCDvg4F4Xu7mbr7jDz+fokgfbLmnq7hxqJqoRyWs58Fg+w==";
        assertThat(SnapSignature.hmac("secret-xyz", stringToSign)).isEqualTo(expected);
        assertThat(SnapSignature.verifyHmac("secret-xyz", stringToSign, expected)).isTrue();
        assertThat(SnapSignature.verifyHmac("other", stringToSign, expected)).isFalse();
        assertThat(SnapSignature.verifyHmac("secret-xyz", stringToSign, "%%%")).isFalse();
    }

    @Test
    @DisplayName("Hex signatures are recognised as hex, Base64 is not")
    void looksHex() {
        assertThat(SnapSignature.looksHex("a4d8ff00")).isTrue();
        assertThat(SnapSignature.looksHex("abc")).isFalse();
        assertThat(SnapSignature.looksHex("pNiXQ03z+w==")).isFalse();
    }
}
