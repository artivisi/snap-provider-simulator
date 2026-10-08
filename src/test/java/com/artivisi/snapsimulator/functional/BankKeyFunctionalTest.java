package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.config.BankKeys;
import com.artivisi.snapsimulator.enums.Bank;
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
    @DisplayName("Each bank's public key is downloadable without login and verifies that bank's signatures only")
    void publicKeyDownload() {
        for (Bank bank : Bank.values()) {
            APIResponse response = api.get("/keys/" + bank.slug() + "-public.pem");
            assertThat(response.status()).isEqualTo(200);
            var published = PemKeys.parsePublicKey(response.text());
            String own = SnapSignature.signRsa(bankKeys.of(bank).privateKey(), "bank|2026-10-08T10:00:00+07:00");
            assertThat(SnapSignature.verifyRsa(published, "bank|2026-10-08T10:00:00+07:00", own)).isTrue();
            Bank other = bank == Bank.BRI ? Bank.BCA : Bank.BRI;
            String foreign = SnapSignature.signRsa(bankKeys.of(other).privateKey(), "bank|2026-10-08T10:00:00+07:00");
            assertThat(SnapSignature.verifyRsa(published, "bank|2026-10-08T10:00:00+07:00", foreign)).isFalse();
        }
    }

    @Test
    @DisplayName("Liveness probe answers UP without login")
    void liveness() {
        APIResponse response = api.get("/actuator/health/liveness");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text()).contains("\"status\":\"UP\"");
    }
}
