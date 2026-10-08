package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.enums.Bank;
import com.artivisi.snapsimulator.snap.PemKeys;
import com.artivisi.snapsimulator.support.SnapTestClient;
import com.artivisi.snapsimulator.util.Randoms;
import com.microsoft.playwright.Download;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.security.KeyPair;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class PortalFunctionalTest extends PlaywrightTestBase {

    private String email;
    private final String password = "password-" + Randoms.alphanumeric(6);

    private void signUpAndLogIn() {
        email = "ui-" + Randoms.digits(10) + "@example.test";
        page.navigate("/portal/signup");
        page.locator("#email").fill(email);
        page.locator("#password").fill(password);
        page.locator("#signup-submit").click();
        assertThat(page.locator("#registered-message")).isVisible();
        page.locator("#email").fill(email);
        page.locator("#password").fill(password);
        page.locator("#login-submit").click();
        assertThat(page.locator("#account-email")).hasText(email);
    }

    /** Adds a connection from the account page; returns the issued secret, leaves the issued page open. */
    private String addConnection(Bank bank, boolean diagnostic) {
        page.navigate("/portal");
        page.locator("input[name='bank'][value='" + bank + "']").check();
        page.locator("#tokenTtlSeconds").fill("300");
        page.locator(diagnostic ? "#diagnosticMode1" : "#diagnosticMode2").check();
        page.locator("#connection-submit").click();
        assertThat(page.locator("#issued-bank")).hasText(bank.name());
        assertThat(page.locator("#issued-client-id")).hasText(Pattern.compile("[A-Za-z0-9]{32}"));
        return page.locator("#issued-client-secret").textContent();
    }

    @Test
    @SpecRef("sim.portal.signup")
    @SpecRef("sim.portal.connection")
    @SpecRef("sim.portal.checklist")
    @SpecRef("sim.portal.credentials")
    @DisplayName("Sign up, add BRI and BCA connections with their own credentials, regenerate a secret")
    void signupAndConnections() {
        signUpAndLogIn();
        assertThat(page.locator("#no-connections")).isVisible();

        String briSecret = addConnection(Bank.BRI, true);
        assertThat(briSecret).hasSize(48);
        assertThat(page.locator("#issued-partner-service-id")).hasText(Pattern.compile("\" +\\d+\""));
        page.locator("#continue-link").click();
        assertThat(page.locator("#connection-bank")).hasText("BRI");
        assertThat(page.locator("#checklist tbody tr")).hasCount(7);
        assertThat(page.locator("#checklist-KEY_REGISTERED")).hasAttribute("data-done", "false");
        assertThat(page.locator("#diagnostic-mode")).hasText("On");
        assertThat(page.locator("#nav-vas")).isVisible();
        assertThat(page.content()).doesNotContain(briSecret);

        page.locator("#regenerate-secret").click();
        assertThat(page.locator("#issued-client-secret").textContent()).hasSize(48).isNotEqualTo(briSecret);

        addConnection(Bank.BCA, false);
        page.locator("#continue-link").click();
        assertThat(page.locator("#connection-bank")).hasText("BCA");
        assertThat(page.locator("#checklist tbody tr")).hasCount(6);
        assertThat(page.locator("#checklist-VA_CREATED")).hasCount(0);
        assertThat(page.locator("#nav-vas")).hasCount(0);
        assertThat(page.locator("#company-code")).hasText(Pattern.compile("\\d+"));
        assertThat(page.locator("#snap-endpoints")).containsText("/openapi/v2.0/transfer-va/status");

        page.locator("#nav-account").click();
        assertThat(page.locator("#connections tbody tr")).hasCount(2);
    }

    @Test
    @DisplayName("Sign-up rejects a taken email; adding a connection needs a bank and a diagnostic choice")
    void validation() {
        signUpAndLogIn();
        page.locator("#tokenTtlSeconds").fill("300");
        page.locator("#connection-form").evaluate("f => f.noValidate = true");
        page.locator("#connection-submit").click();
        assertThat(page.locator("#connection-form")).containsText("choose a bank");
        assertThat(page.locator("#connection-form")).containsText("choose on or off");

        page.locator("#nav-logout").click();
        page.navigate("/portal/signup");
        page.locator("#email").fill(email);
        page.locator("#password").fill(password);
        page.locator("#signup-submit").click();
        assertThat(page.locator("#email-error")).hasText("Email is already registered");
    }

    @Test
    @SpecRef("sim.portal.key-generate")
    @DisplayName("Generating a key downloads a PKCS#8 private key that obtains a token; the public key is registered")
    void generateKey() throws Exception {
        signUpAndLogIn();
        String secret = addConnection(Bank.BRI, false);
        String clientId = page.locator("#issued-client-id").textContent();
        String partnerServiceId = page.locator("#issued-partner-service-id").textContent().replace("\"", "");
        page.locator("#continue-link").click();
        page.locator("#nav-key").click();
        assertThat(page.locator("#no-key")).isVisible();
        assertThat(page.locator("#bank-key-link")).hasText("bri-public.pem");

        Download download = page.waitForDownload(() -> page.locator("#generate-key").click());
        assertThat(download.suggestedFilename()).isEqualTo("private.pem");
        String privatePem = Files.readString(download.path());
        SnapTestClient client = new SnapTestClient(api, Bank.BRI, clientId, partnerServiceId,
                PemKeys.parsePrivateKey(privatePem), secret);
        assertThat(client.obtainToken()).hasSize(64);

        page.reload();
        assertThat(page.locator("#registered-key")).containsText("BEGIN PUBLIC KEY");
        page.locator("#nav-overview").click();
        assertThat(page.locator("#checklist-KEY_REGISTERED")).hasAttribute("data-done", "true");
        assertThat(page.locator("#checklist-TOKEN_OBTAINED")).hasAttribute("data-done", "true");
    }

    @Test
    @SpecRef("sim.portal.key-upload")
    @DisplayName("Upload page shows the openssl commands, accepts a public key and rejects a private key")
    void uploadKey() {
        signUpAndLogIn();
        addConnection(Bank.BCA, false);
        page.locator("#continue-link").click();
        page.locator("#nav-key").click();
        assertThat(page.locator("#openssl-commands")).containsText("openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048");
        assertThat(page.locator("#bank-key-link")).hasText("bca-public.pem");

        KeyPair pair = PemKeys.generateRsa();
        page.locator("#public-key-pem").fill(PemKeys.toPem(pair.getPrivate()));
        page.locator("#upload-key").click();
        assertThat(page.locator("#key-error")).containsText("This is a private key");
        assertThat(page.locator("#no-key")).isVisible();

        page.locator("#upload-key").click();
        assertThat(page.locator("#key-error")).hasText("Paste the public key or choose a .pem file.");

        page.locator("#public-key-pem").fill(PemKeys.toPem(pair.getPublic()));
        page.locator("#upload-key").click();
        assertThat(page.locator("#flash-message")).hasText("Public key registered.");
        assertThat(page.locator("#registered-key")).hasText(PemKeys.toPem(pair.getPublic()).trim());
    }

    @Test
    @SpecRef("sim.portal.settings")
    @SpecRef("sim.portal.endpoint")
    @DisplayName("Settings and partner endpoint are saved and shown on the connection overview")
    void settingsAndEndpoint() {
        signUpAndLogIn();
        addConnection(Bank.BRI, false);
        page.locator("#continue-link").click();
        page.locator("#nav-settings").click();
        page.locator("#tokenTtlSeconds").fill("45");
        page.locator("#diagnosticMode1").check();
        page.locator("#settings-submit").click();
        assertThat(page.locator("#token-ttl")).hasText("45 s");
        assertThat(page.locator("#diagnostic-mode")).hasText("On");

        page.locator("#nav-settings").click();
        page.locator("#tokenTtlSeconds").fill("5");
        page.locator("#settings-form").evaluate("f => f.noValidate = true");
        page.locator("#settings-submit").click();
        assertThat(page.locator("#settings-form .field-error")).isVisible();

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
        portalLogin(partner);
        openConnection(partner, "");
        assertThat(page.locator("#checklist-TOKEN_OBTAINED")).hasAttribute("data-done", "true");

        page.onDialog(dialog -> dialog.accept());
        page.locator("#reset-data").click();
        assertThat(page.locator("#flash-message")).hasText("Simulation data reset.");
        assertThat(page.locator("#checklist-TOKEN_OBTAINED")).hasAttribute("data-done", "false");
        assertThat(page.locator("#checklist-KEY_REGISTERED")).hasAttribute("data-done", "true");
    }

    @Test
    @DisplayName("Portal pages require login; a partner cannot open another partner's connection")
    void access() {
        page.navigate("/portal");
        assertThat(page).hasURL(Pattern.compile(".*/portal/login$"));
        page.locator("#email").fill("nobody@example.test");
        page.locator("#password").fill("wrong-password");
        page.locator("#login-submit").click();
        assertThat(page.locator("#login-error")).isVisible();
        assertThat(api.get("/portal/api/me").status()).isEqualTo(401);

        TestPartner owner = signupWithKey(false, 300);
        TestPartner other = signupWithKey(false, 300);
        assertThat(api.get(conn(owner, ""), basicAuth(other)).status()).isEqualTo(404);
        assertThat(api.get(conn(owner, "/payments"), basicAuth(other)).status()).isEqualTo(404);
    }
}
