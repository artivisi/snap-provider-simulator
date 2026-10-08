package com.artivisi.snapsimulator.snap;

import com.artivisi.snapsimulator.SpecRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpecRef("sim.diagnostic-mode")
class SignatureDiagnosticsTest {

    private static final String SECRET = "s3cret";
    private static final String PATH = "/snap/v1.0/transfer-va/create-va";
    private static final String TOKEN = "tok";
    private static final String TS = "2026-10-08T10:00:00.000+07:00";
    private static final String BODY = "{ \"a\": 1 }";

    private static String hint(String signedString, String query) {
        String signature = SnapSignature.hmac(SECRET, signedString);
        return SignatureDiagnostics.symmetric("POST", PATH, query, TOKEN, BODY, TS, SECRET, signature).get("hint");
    }

    @Test
    @DisplayName("Each common HMAC mistake is named")
    void symmetricHints() {
        String hash = SnapSignature.bodyHash(BODY);
        assertThat(hint("POST:" + PATH + ":" + TOKEN + ":" + hash.toUpperCase(Locale.ROOT) + ":" + TS, null))
                .contains("lowercase hex");
        assertThat(hint("POST:" + PATH + "?x=1:" + TOKEN + ":" + hash + ":" + TS, "x=1")).contains("query string");
        assertThat(hint("POST:" + PATH + ":Bearer " + TOKEN + ":" + hash + ":" + TS, null)).contains("Bearer");
        assertThat(hint("GET:" + PATH + ":" + TOKEN + ":" + hash + ":" + TS, null)).contains("no common mistake");
        assertThat(SignatureDiagnostics.symmetric("POST", PATH, null, TOKEN, BODY, TS, SECRET, "abcdef01").get("hint"))
                .contains("looks hex");
    }

    @Test
    @DisplayName("Output holds the inputs, never the expected signature")
    void noSignatureInOutput() {
        String expectedSignature = SnapSignature.hmac(SECRET, SnapSignature.symmetricStringToSign("POST", PATH, TOKEN, BODY, TS));
        Map<String, String> d = SignatureDiagnostics.symmetric("POST", PATH, null, TOKEN, BODY, TS, SECRET, "AAAA");
        assertThat(d).containsOnlyKeys("expectedStringToSign", "receivedTimestamp", "minifiedBody", "bodySha256",
                "rawBodySha256", "hint");
        assertThat(d.values()).noneMatch(v -> v.contains(expectedSignature));
    }

    @Test
    @DisplayName("Asymmetric: wrong key hint with the registered key fingerprint")
    void asymmetric() {
        KeyPair pair = PemKeys.generateRsa();
        Map<String, String> d = SignatureDiagnostics.asymmetric("client", TS, "AAA=", pair.getPublic());
        assertThat(d.get("expectedStringToSign")).isEqualTo("client|" + TS);
        assertThat(d.get("registeredKeySha256")).hasSize(64);
        assertThat(d.get("hint")).contains("does not verify");
    }
}
