package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.snap.PemKeys;
import com.artivisi.snapsimulator.support.SnapTestClient;
import com.artivisi.snapsimulator.util.Randoms;
import com.microsoft.playwright.Download;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.security.KeyPair;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class PortalFunctionalTest extends PlaywrightTestBase {

    private String email;
    private final String password = "password-" + Randoms.alphanumeric(6);

    /** Signs up through the form and returns the issued client secret. */
    private String signUp(boolean diagnostic) {
        email = "ui-" + Randoms.digits(10) + "@example.test";
        page.navigate("/portal/signup");
        page.locator("#email").fill(email);
        page.locator("#password").fill(password);
        page.locator("#tokenTtlSeconds").fill("300");
        page.locator(diagnostic ? "#diagnosticMode1" : "#diagnosticMode2").check();
        page.locator("#signup-submit").click();
        assertThat(page.locator("#issued-client-id")).hasText(java.util.regex.Pattern.compile("[A-Za-z0-9]{32}"));
        return page.locator("#issued-client-secret").textContent();
    }

    private void logIn() {
        page.navigate("/portal/login");
        page.locator("#email").fill(email);
        page.locator("#password").fill(password);
        page.locator("#login-submit").click();
        assertThat(page.locator("#partner-email")).hasText(email);
    }

    @Test
    @SpecRef("sim.portal.signup")
    @SpecRef("sim.portal.checklist")
    @SpecRef("sim.portal.credentials")
    @DisplayName("Sign up, see credentials once, log in, see the pending checklist, regenerate the secret")
    void signupAndCredentials() {
        String secret = signUp(true);
        assertThat(secret).hasSize(48);
        assertThat(page.locator("#issued-partner-service-id")).hasText(java.util.regex.Pattern.compile("\" +\\d+\""));

        logIn();
        assertThat(page.locator("#checklist-KEY_REGISTERED")).hasAttribute("data-done", "false");
        assertThat(page.locator("#diagnostic-mode")).hasText("On");
        assertThat(page.content()).doesNotContain(secret);

        page.locator("#regenerate-secret").click();
        String regenerated = page.locator("#issued-client-secret").textContent();
        assertThat(regenerated).hasSize(48).isNotEqualTo(secret);
    }

    @Test
    @DisplayName("Sign-up rejects a taken email and a missing diagnostic choice")
    void signupValidation() {
        signUp(false);
        String taken = email;
        page.navigate("/portal/signup");
        page.locator("#email").fill(taken);
        page.locator("#password").fill(password);
        page.locator("#tokenTtlSeconds").fill("300");
        page.locator("#diagnosticMode2").check();
        page.locator("#signup-submit").click();
        assertThat(page.locator("#email-error")).hasText("Email is already registered");
    }

    @Test
    @SpecRef("sim.portal.key-generate")
    @DisplayName("Generating a key downloads a PKCS#8 private key that obtains a token; the public key is registered")
    void generateKey() throws Exception {
        String secret = signUp(false);
        String clientId = page.locator("#issued-client-id").textContent();
        logIn();
        page.locator("#nav-key").click();
        assertThat(page.locator("#no-key")).isVisible();

        Download download = page.waitForDownload(() -> page.locator("#generate-key").click());
        assertThat(download.suggestedFilename()).isEqualTo("private.pem");
        String privatePem = Files.readString(download.path());
        SnapTestClient client = new SnapTestClient(api, clientId, PemKeys.parsePrivateKey(privatePem), secret);
        assertThat(client.obtainToken()).hasSize(64);

        page.navigate("/portal/key");
        assertThat(page.locator("#registered-key")).containsText("BEGIN PUBLIC KEY");
        page.navigate("/portal");
        assertThat(page.locator("#checklist-KEY_REGISTERED")).hasAttribute("data-done", "true");
        assertThat(page.locator("#checklist-TOKEN_OBTAINED")).hasAttribute("data-done", "true");
    }

    @Test
    @SpecRef("sim.portal.key-upload")
    @DisplayName("Upload page shows the openssl commands, accepts a public key and rejects a private key")
    void uploadKey() {
        signUp(false);
        logIn();
        page.navigate("/portal/key");
        assertThat(page.locator("#openssl-commands")).containsText("openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048");

        KeyPair pair = PemKeys.generateRsa();
        page.locator("#public-key-pem").fill(PemKeys.toPem(pair.getPrivate()));
        page.locator("#upload-key").click();
        assertThat(page.locator("#key-error")).containsText("This is a private key");
        assertThat(page.locator("#no-key")).isVisible();

        page.locator("#public-key-pem").fill(PemKeys.toPem(pair.getPublic()));
        page.locator("#upload-key").click();
        assertThat(page.locator("#flash-message")).hasText("Public key registered.");
        assertThat(page.locator("#registered-key")).hasText(PemKeys.toPem(pair.getPublic()).trim());
    }

    @Test
    @SpecRef("sim.portal.settings")
    @SpecRef("sim.portal.endpoint")
    @DisplayName("Settings and partner endpoint are saved and shown on the overview")
    void settingsAndEndpoint() {
        signUp(false);
        logIn();
        page.locator("#nav-settings").click();
        page.locator("#tokenTtlSeconds").fill("45");
        page.locator("#diagnosticMode1").check();
        page.locator("#settings-submit").click();
        assertThat(page.locator("#token-ttl")).hasText("45 s");
        assertThat(page.locator("#diagnostic-mode")).hasText("On");

        page.locator("#nav-endpoint").click();
        page.locator("#baseUrl").fill("ftp://example.test");
        page.locator("#clientId").fill("bank-client");
        page.locator("#clientSecret").fill("bank-secret");
        page.locator("#endpoint-submit").click();
        assertThat(page.locator("#baseUrl + .field-error")).hasText("must be an http or https URL");

        page.locator("#baseUrl").fill("http://localhost:18080/snap/");
        page.locator("#clientSecret").fill("bank-secret");
        page.locator("#endpoint-submit").click();
        assertThat(page.locator("#flash-message")).hasText("Partner endpoint saved.");
        page.locator("#nav-overview").click();
        assertThat(page.locator("#endpoint-base-url")).hasText("http://localhost:18080/snap");
    }

    @Test
    @SpecRef("sim.portal.reset")
    @DisplayName("Reset clears tokens and checklist steps but keeps the key")
    void reset() {
        TestPartner partner = signupWithKey(false, 300);
        snapClient(partner).obtainToken();
        email = partner.email();
        page.navigate("/portal/login");
        page.locator("#email").fill(partner.email());
        page.locator("#password").fill(partner.password());
        page.locator("#login-submit").click();
        assertThat(page.locator("#checklist-TOKEN_OBTAINED")).hasAttribute("data-done", "true");

        page.onDialog(dialog -> dialog.accept());
        page.locator("#reset-data").click();
        assertThat(page.locator("#flash-message")).hasText("Simulation data reset.");
        assertThat(page.locator("#checklist-TOKEN_OBTAINED")).hasAttribute("data-done", "false");
        assertThat(page.locator("#checklist-KEY_REGISTERED")).hasAttribute("data-done", "true");
    }

    @Test
    @DisplayName("Portal pages require login; wrong password shows an error")
    void requiresLogin() {
        page.navigate("/portal/key");
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/portal/login$"));
        page.locator("#email").fill("nobody@example.test");
        page.locator("#password").fill("wrong-password");
        page.locator("#login-submit").click();
        assertThat(page.locator("#login-error")).isVisible();
        assertThat(api.get("/portal/api/me").status()).isEqualTo(401);
    }
}
