package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.TestcontainersConfiguration;
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
}
