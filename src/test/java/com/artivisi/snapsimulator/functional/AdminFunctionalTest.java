package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.enums.Bank;
import com.artivisi.snapsimulator.support.SnapTestClient;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

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
    @SpecRef("sim.portal.reset")
    @DisplayName("Operator sees each partner with its bank connections, disables and enables, resets and deletes")
    void managePartners() {
        TestPartner bri = signupWithKey(Bank.BRI, false, 300);
        TestPartner bca = addConnection(bri.email(), bri.password(), Bank.BCA, false, 300);
        SnapTestClient client = snapClient(bri);
        client.obtainToken();
        page.onDialog(dialog -> dialog.accept());
        logInAsOperator();

        Locator partner = page.locator("[data-testid='partner-" + bri.email() + "']");
        assertThat(partner.locator("tr.connection")).hasCount(2);
        assertThat(partner.locator("tr.connection[data-bank='BRI'] .checklist-progress")).hasText("2 / 7");
        assertThat(partner.locator("tr.connection[data-bank='BCA'] .checklist-progress")).hasText("1 / 6");

        partner.locator(".action-disable").click();
        assertThat(page.locator("#flash-message")).hasText("Partner disabled.");
        assertThat(partner.locator(".status")).hasText("Disabled");
        assertCode(client.token(client.tokenHeaders(SnapTestClient.now()), "{\"grantType\":\"client_credentials\"}"),
                "4017300", "Unauthorized. Client disabled");
        SnapTestClient bcaClient = snapClient(bca);
        assertCode(bcaClient.token(bcaClient.tokenHeaders(SnapTestClient.now()), "{\"grantType\":\"client_credentials\"}"),
                "4017300", "Unauthorized. Client disabled");

        partner.locator(".action-enable").click();
        assertThat(partner.locator(".status")).hasText("Enabled");

        partner.locator("tr.connection[data-bank='BRI'] .action-reset").click();
        assertThat(page.locator("#flash-message")).hasText("Connection data reset.");
        assertThat(partner.locator("tr.connection[data-bank='BRI'] .checklist-progress")).hasText("1 / 7");

        partner.locator(".action-delete").click();
        assertThat(page.locator("#flash-message")).hasText("Partner deleted.");
        assertThat(partner).hasCount(0);
        assertThat(api.get("/portal/api/me", basicAuth(bri)).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Admin requires the operator login; partner credentials do not work there")
    void requiresOperator() {
        TestPartner partner = signupWithKey(false, 300);
        page.navigate("/admin");
        assertThat(page).hasURL(Pattern.compile(".*/admin/login$"));
        page.locator("#username").fill(partner.email());
        page.locator("#password").fill(partner.password());
        page.locator("#login-submit").click();
        assertThat(page.locator("#login-error")).isVisible();
    }
}
