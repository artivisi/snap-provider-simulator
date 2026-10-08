package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.TestcontainersConfiguration;
import com.artivisi.snapsimulator.snap.PemKeys;
import com.artivisi.snapsimulator.support.SnapTestClient;
import com.artivisi.snapsimulator.util.Randoms;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import tools.jackson.databind.JsonNode;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Base64;

/**
 * Browser and API context against the running app. Headed runs:
 * -Dplaywright.headless=false.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class PlaywrightTestBase {

    protected static Playwright playwright;
    protected static Browser browser;
    protected BrowserContext context;
    protected Page page;
    protected APIRequestContext api;

    @LocalServerPort
    protected int port;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(!"false".equals(System.getProperty("playwright.headless"))));
    }

    @AfterAll
    static void closeBrowser() {
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void openContext() {
        context = browser.newContext(new Browser.NewContextOptions().setBaseURL(baseUrl()).setViewportSize(1440, 900));
        page = context.newPage();
        page.setDefaultTimeout(15_000);
        api = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl()));
    }

    @AfterEach
    void closeContext() {
        api.dispose();
        context.close();
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /** A partner registered through the portal API, with a generated key pair. */
    public record TestPartner(String email, String password, String partnerServiceId, String clientId,
            String clientSecret, PrivateKey privateKey) {
    }

    protected TestPartner signupWithKey(boolean diagnosticMode, int tokenTtlSeconds) {
        String email = "partner-" + Randoms.digits(12) + "@example.test";
        String password = "password-" + Randoms.alphanumeric(8);
        APIResponse signup = api.post("/portal/api/signup", RequestOptions.create().setData(
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"tokenTtlSeconds\":"
                        + tokenTtlSeconds + ",\"diagnosticMode\":" + diagnosticMode + "}")
                .setHeader("Content-Type", "application/json"));
        if (signup.status() != 201) {
            throw new IllegalStateException("signup failed: " + signup.status() + " " + signup.text());
        }
        JsonNode issued = SnapTestClient.json(signup);
        APIResponse key = api.post("/portal/api/key/generate", basicAuth(email, password));
        if (key.status() != 200) {
            throw new IllegalStateException("key generation failed: " + key.status() + " " + key.text());
        }
        PrivateKey privateKey = PemKeys.parsePrivateKey(SnapTestClient.json(key).get("privateKeyPem").asString());
        return new TestPartner(email, password, issued.get("partnerServiceId").asString(),
                issued.get("clientId").asString(), issued.get("clientSecret").asString(), privateKey);
    }

    protected RequestOptions basicAuth(String email, String password) {
        return RequestOptions.create().setHeader("Authorization", "Basic "
                + Base64.getEncoder().encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8)));
    }

    protected RequestOptions basicAuth(TestPartner partner) {
        return basicAuth(partner.email(), partner.password());
    }

    protected SnapTestClient snapClient(TestPartner partner) {
        return new SnapTestClient(api, partner.clientId(), partner.privateKey(), partner.clientSecret());
    }

    protected JsonNode me(TestPartner partner) {
        return SnapTestClient.json(api.get("/portal/api/me", basicAuth(partner)));
    }

    /** Completion time of a checklist step from /portal/api/me, or null. */
    protected String checklistDone(TestPartner partner, String item) {
        for (JsonNode step : me(partner).get("checklist")) {
            if (item.equals(step.get("item").asString())) {
                JsonNode at = step.get("completedAt");
                return at == null || at.isNull() ? null : at.asString();
            }
        }
        throw new IllegalArgumentException("no checklist item " + item);
    }
}
