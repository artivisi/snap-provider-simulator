package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.config.BankKeys;
import com.artivisi.snapsimulator.snap.PemKeys;
import com.artivisi.snapsimulator.snap.SnapSignature;
import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class BankKeyFunctionalTest extends PlaywrightTestBase {

    @Autowired
    BankKeys bankKeys;

    @Test
    @SpecRef("sim.bank-key.publish")
    @DisplayName("Bank public key is downloadable without login and verifies bank signatures")
    void publicKeyDownload() {
        APIResponse response = api.get("/keys/bank-public.pem");
        assertThat(response.status()).isEqualTo(200);
        String signature = SnapSignature.signRsa(bankKeys.privateKey(), "bank|2026-10-08T10:00:00.000+07:00");
        assertThat(SnapSignature.verifyRsa(PemKeys.parsePublicKey(response.text()),
                "bank|2026-10-08T10:00:00.000+07:00", signature)).isTrue();
    }

    @Test
    @DisplayName("Liveness probe answers UP without login")
    void liveness() {
        APIResponse response = api.get("/actuator/health/liveness");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text()).contains("\"status\":\"UP\"");
    }
}
