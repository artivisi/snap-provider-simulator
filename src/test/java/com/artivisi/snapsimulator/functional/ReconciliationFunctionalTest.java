package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.config.BankKeys;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import com.artivisi.snapsimulator.support.FakePartnerApp;
import com.artivisi.snapsimulator.support.SnapTestClient;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationFunctionalTest extends PlaywrightTestBase {

    @Autowired
    BankKeys bankKeys;

    private FakePartnerApp app;
    private TestPartner partner;

    @BeforeEach
    void setUp() throws Exception {
        app = new FakePartnerApp("bank-id", "bank-secret", bankKeys.publicKey());
        partner = signupWithKey(false, 300);
        api.put("/portal/api/endpoint", json().setData("{\"baseUrl\":\"" + app.baseUrl()
                + "\",\"clientId\":\"bank-id\",\"clientSecret\":\"bank-secret\"}"));
    }

    @AfterEach
    void tearDown() {
        app.close();
    }

    private RequestOptions json() {
        return basicAuth(partner).setHeader("Content-Type", "application/json");
    }

    private String va(int n) {
        return partner.partnerServiceId() + "70" + n;
    }

    private static String today() {
        return LocalDate.now(SnapTimestamp.JAKARTA).toString();
    }

    @Test
    @SpecRef("sim.reconciliation-seeder")
    @SpecRef("sim.statement-csv")
    @DisplayName("Seeder: two normal, one dropped, one amount mismatch, one duplicate; statement has one credit each")
    void seedAndStatement() {
        APIResponse response = api.post("/portal/api/reconciliation-seed", json().setData("{\"virtualAccountNos\":[\""
                + va(1) + "\",\"" + va(2) + "\",\"" + va(3) + "\",\"" + va(4) + "\",\"" + va(5) + "\"],\"channelId\":\"00002\"}"));
        assertThat(response.status()).as(response.text()).isEqualTo(200);
        JsonNode results = SnapTestClient.json(response);
        assertThat(results.findValuesAsString("scenario"))
                .containsExactly("NORMAL", "NORMAL", "DROPPED", "AMOUNT_MISMATCH", "DUPLICATE");
        assertThat(results.get(2).at("/result/notificationStatus").asString()).isEqualTo("DROPPED");

        List<FakePartnerApp.Received> payments = app.received("/payment");
        assertThat(payments).hasSize(5);
        assertThat(payments).noneMatch(p -> p.body().get("virtualAccountNo").asString().equals(va(3)));
        FakePartnerApp.Received mismatch = payments.stream()
                .filter(p -> p.body().get("virtualAccountNo").asString().equals(va(4))).findFirst().orElseThrow();
        assertThat(mismatch.body().at("/paidAmount/value").asString()).isEqualTo("251000.00");
        List<FakePartnerApp.Received> duplicate = payments.stream()
                .filter(p -> p.body().get("virtualAccountNo").asString().equals(va(5))).toList();
        assertThat(duplicate).hasSize(2);
        assertThat(duplicate.get(0).headers().get("x-external-id")).isNotEqualTo(duplicate.get(1).headers().get("x-external-id"));

        APIResponse statement = api.get("/portal/api/statements/" + today() + ".csv", basicAuth(partner));
        assertThat(statement.headers().get("content-type")).startsWith("text/csv");
        String[] lines = statement.text().split("\n");
        assertThat(lines[0]).isEqualTo("transaction_date,transaction_id,type,amount,currency,virtual_account_no,remark");
        assertThat(lines).hasSize(6);
        for (int i = 1; i <= 5; i++) {
            assertThat(lines[i]).matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\+07:00,J\\d{20},CREDIT,250000\\.00,IDR,\""
                    + java.util.regex.Pattern.quote(va(i)) + "\",\"VA payment .*\"");
        }
    }

    @Test
    @SpecRef("sim.statement-csv")
    @DisplayName("A reversed payment shows a credit and a debit; another day's statement is empty")
    void reversalAndOtherDay() {
        app.answerPayment(body -> new FakePartnerApp.Answer(200, FakePartnerApp.paymentReply(body, "2002500", "01")));
        api.post("/portal/api/biller-payments", json().setData("{\"virtualAccountNo\":\"" + va(9)
                + "\",\"amount\":\"1500.00\",\"channelId\":\"00002\"}"));
        String csv = api.get("/portal/api/statements/" + today() + ".csv", basicAuth(partner)).text();
        assertThat(csv).contains(",CREDIT,1500.00,IDR,").contains(",DEBIT,1500.00,IDR,").contains("Reversal");
        String other = api.get("/portal/api/statements/2020-01-01.csv", basicAuth(partner)).text();
        assertThat(other).isEqualTo("transaction_date,transaction_id,type,amount,currency,virtual_account_no,remark\n");
    }

    @Test
    @DisplayName("Seeder needs a partner endpoint and exactly five VA numbers")
    void seederValidation() {
        api.delete("/portal/api/endpoint", basicAuth(partner));
        String five = "{\"virtualAccountNos\":[\"1\",\"2\",\"3\",\"4\",\"5\"],\"channelId\":\"00002\"}";
        assertThat(api.post("/portal/api/reconciliation-seed", json().setData(five)).text())
                .contains("Register the partner endpoint first");
        APIResponse four = api.post("/portal/api/reconciliation-seed", json().setData(
                "{\"virtualAccountNos\":[\"1\",\"2\",\"3\",\"4\"],\"channelId\":\"00002\"}"));
        assertThat(four.status()).isEqualTo(400);
        assertThat(SnapTestClient.json(four).at("/fieldErrors/virtualAccountNos").isMissingNode()).isFalse();
    }
}
