package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.support.SnapTestClient;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.artivisi.snapsimulator.functional.AccessTokenFunctionalTest.assertCode;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

@SpecRef("sim.admin.partners")
class AdminFunctionalTest extends PlaywrightTestBase {

    private void logInAsOperator() {
        page.navigate("/admin/login");
        page.locator("#username").fill("operator");
        page.locator("#password").fill("operator-test-password");
        page.locator("#login-submit").click();
        assertThat(page.locator("h1")).hasText("Partners");
    }

    @Test
    @DisplayName("Operator sees partners with checklist progress, disables and enables one, resets and deletes it")
    void managePartners() {
        TestPartner partner = signupWithKey(false, 300);
        SnapTestClient client = snapClient(partner);
        client.obtainToken();
        page.onDialog(dialog -> dialog.accept());
        logInAsOperator();

        Locator row = page.locator("[data-testid='partner-" + partner.email() + "']");
        assertThat(row.locator(".checklist-progress")).hasText("2 / 7");

        row.locator(".action-disable").click();
        assertThat(page.locator("#flash-message")).hasText("Partner disabled.");
        assertThat(row.locator(".status")).hasText("Disabled");
        assertCode(client.token(client.tokenHeaders(SnapTestClient.now()), "{\"grantType\":\"client_credentials\"}"),
                "4017300", "Unauthorized. Client disabled");

        row.locator(".action-enable").click();
        assertThat(row.locator(".status")).hasText("Enabled");

        row.locator(".action-reset").click();
        assertThat(row.locator(".checklist-progress")).hasText("1 / 7");

        row.locator(".action-delete").click();
        assertThat(page.locator("#flash-message")).hasText("Partner deleted.");
        assertThat(row).hasCount(0);
        assertThat(api.get("/portal/api/me", basicAuth(partner)).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Admin requires the operator login; partner credentials do not work there")
    void requiresOperator() {
        TestPartner partner = signupWithKey(false, 300);
        page.navigate("/admin");
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/admin/login$"));
        page.locator("#username").fill(partner.email());
        page.locator("#password").fill(partner.password());
        page.locator("#login-submit").click();
        assertThat(page.locator("#login-error")).isVisible();
    }
}
