package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.config.BankKeys;
import com.artivisi.snapsimulator.support.FakePartnerApp;
import com.artivisi.snapsimulator.support.SnapTestClient;
import com.microsoft.playwright.Download;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/** The portal pages a partner uses after onboarding, driven through the browser. */
class WorkspaceFunctionalTest extends PlaywrightTestBase {

    @Autowired
    BankKeys bankKeys;

    private FakePartnerApp app;
    private TestPartner partner;

    @BeforeEach
    void setUp() throws Exception {
        app = new FakePartnerApp("bank-id", "bank-secret", bankKeys.publicKey());
        partner = signupWithKey(false, 300);
        page.navigate("/portal/login");
        page.locator("#email").fill(partner.email());
        page.locator("#password").fill(partner.password());
        page.locator("#login-submit").click();
        assertThat(page.locator("#partner-email")).hasText(partner.email());
        page.locator("#nav-endpoint").click();
        page.locator("#baseUrl").fill(app.baseUrl());
        page.locator("#clientId").fill("bank-id");
        page.locator("#clientSecret").fill("bank-secret");
        page.locator("#endpoint-submit").click();
        assertThat(page.locator("#flash-message")).hasText("Partner endpoint saved.");
    }

    @AfterEach
    void tearDown() {
        app.close();
    }

    @Test
    @SpecRef("sim.portal.endpoint")
    @DisplayName("Test connection button shows the partner's token answer")
    void testConnection() {
        page.locator("#test-connection").click();
        assertThat(page.locator("#connection-outcome")).hasText("Reachable: the partner issued a token to the bank.");
    }

    @Test
    @SpecRef("sim.va.pay")
    @SpecRef("sim.exchange-log")
    @DisplayName("Pay a VA from the VA page; the exchange log shows the inbound create-va and the outbound payment call")
    void payVaAndExchangeLog() {
        SnapTestClient client = snapClient(partner);
        String token = client.obtainToken();
        client.call("POST", "/snap/v1.0/transfer-va/create-va", token, """
                {"partnerServiceId":"%s","customerNo":"31","virtualAccountNo":"%s31","virtualAccountName":"Dewi",
                 "trxId":"UI-1","totalAmount":{"value":"99000.00","currency":"IDR"}}"""
                .formatted(partner.partnerServiceId(), partner.partnerServiceId()));
        page.locator("#nav-vas").click();
        var row = page.locator("[data-testid='va-UI-1']");
        row.locator("select.channel").selectOption("00002");
        row.locator("button.pay").click();
        assertThat(page.locator("#flash-message")).containsText("Payment ACKNOWLEDGED");
        assertThat(row.locator(".va-status")).hasText("PAID");

        page.locator("#nav-exchanges").click();
        assertThat(page.locator("details.exchange").first()).containsText("POST " + app.baseUrl() + "/v1.0/transfer-va/payment");
        var create = page.locator("details.exchange", new com.microsoft.playwright.Page.LocatorOptions()
                .setHasText("/snap/v1.0/transfer-va/create-va"));
        create.locator("summary").click();
        assertThat(create.locator(".string-to-sign")).containsText("POST:/snap/v1.0/transfer-va/create-va:" + token + ":");
    }

    @Test
    @SpecRef("sim.biller-payment.trigger")
    @SpecRef("sim.biller-payment.resend")
    @SpecRef("sim.statement-csv")
    @DisplayName("Simulate a biller-hosted payment, resend it, download the statement")
    void billerPaymentResendStatement() throws Exception {
        page.locator("#nav-payments").click();
        page.locator("#biller-va").fill(partner.partnerServiceId() + "4401");
        page.locator("#biller-amount").fill("250000.00");
        page.locator("#biller-channel").selectOption("00003");
        page.locator("#biller-submit").click();
        assertThat(page.locator("#flash-message")).containsText("Payment ACKNOWLEDGED");
        assertThat(page.locator("#payments .notification")).hasText("ACKNOWLEDGED");

        page.locator("button.resend").click();
        assertThat(page.locator("#flash-message")).containsText("responseCode 4092500");
        assertThat(app.received("/payment")).hasSize(2);

        Download download = page.waitForDownload(() -> page.locator("#statement-download").click());
        String csv = Files.readString(download.path());
        assertThat(csv).startsWith("transaction_date,transaction_id,type,amount").contains(",CREDIT,250000.00,IDR,");
    }

    @Test
    @SpecRef("sim.reconciliation-seeder")
    @DisplayName("Reconciliation scenario from the payments page")
    void seed() {
        page.locator("#nav-payments").click();
        StringBuilder vas = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            vas.append(partner.partnerServiceId()).append("90").append(i).append('\n');
        }
        page.locator("#seed-vas").fill(vas.toString());
        page.locator("#seed-submit").click();
        assertThat(page.locator("#flash-message"))
                .hasText("Seeded: NORMAL ACKNOWLEDGED; NORMAL ACKNOWLEDGED; DROPPED DROPPED; AMOUNT_MISMATCH ACKNOWLEDGED; DUPLICATE ACKNOWLEDGED; ");
        assertThat(page.locator("#payments tbody tr")).hasCount(5);
    }

    @Test
    @SpecRef("sim.error-injection")
    @DisplayName("Add a rule from the page, see it listed, delete it; invalid rules show the reason")
    void injectionRules() {
        page.locator("#nav-injections").click();
        page.locator("#injection-type").selectOption("SLOW_RESPONSE");
        page.locator("#injection-target").selectOption("PAYMENT");
        page.locator("#injection-delay").fill("100");
        page.locator("#injection-remaining").fill("1");
        page.locator("#injection-submit").click();
        assertThat(page.locator("#flash-error")).containsText("SLOW_RESPONSE applies to ACCESS_TOKEN_B2B");

        page.locator("#injection-type").selectOption("HTTP_ERROR");
        page.locator("#injection-target").selectOption("CREATE_VA");
        page.locator("#injection-status").selectOption("503");
        page.locator("#injection-remaining").fill("2");
        page.locator("#injection-submit").click();
        assertThat(page.locator("#flash-message")).hasText("Rule added: HTTP_ERROR on CREATE_VA");
        assertThat(page.locator("#rules .rule")).hasCount(1);
        page.locator("button.delete-rule").click();
        assertThat(page.locator("#no-rules")).isVisible();
    }
}
