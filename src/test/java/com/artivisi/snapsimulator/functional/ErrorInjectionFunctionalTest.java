package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.config.BankKeys;
import com.artivisi.snapsimulator.support.FakePartnerApp;
import com.artivisi.snapsimulator.support.SnapTestClient;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static com.artivisi.snapsimulator.functional.AccessTokenFunctionalTest.assertCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpecRef("sim.error-injection")
class ErrorInjectionFunctionalTest extends PlaywrightTestBase {

    private static final String CREATE = "/snap/v1.0/transfer-va/create-va";
    private static final String INQUIRY = "/snap/v1.0/transfer-va/inquiry-va";

    @Autowired
    BankKeys bankKeys;

    private FakePartnerApp app;
    private TestPartner partner;
    private SnapTestClient client;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        app = new FakePartnerApp("bank-id", "bank-secret", bankKeys.publicKey());
        partner = signupWithKey(false, 300);
        assertThat(api.put("/portal/api/endpoint", json().setData("{\"baseUrl\":\"" + app.baseUrl()
                + "\",\"clientId\":\"bank-id\",\"clientSecret\":\"bank-secret\"}")).status()).isEqualTo(204);
        client = snapClient(partner);
        token = client.obtainToken();
    }

    @AfterEach
    void tearDown() {
        app.close();
    }

    private RequestOptions json() {
        return basicAuth(partner).setHeader("Content-Type", "application/json");
    }

    private APIResponse addRule(String body) {
        return api.post("/portal/api/injections", json().setData(body));
    }

    private String create(String customerNo, String trxId) {
        return """
                {"partnerServiceId":"%s","customerNo":"%s","virtualAccountNo":"%s%s","virtualAccountName":"A",
                 "trxId":"%s","totalAmount":{"value":"1000.00","currency":"IDR"}}"""
                .formatted(partner.partnerServiceId(), customerNo, partner.partnerServiceId(), customerNo, trxId);
    }

    private JsonNode biller(String customerNo) {
        return SnapTestClient.json(api.post("/portal/api/biller-payments", json().setData("{\"virtualAccountNo\":\""
                + partner.partnerServiceId() + customerNo + "\",\"amount\":\"5000.00\",\"channelId\":\"00002\"}")));
    }

    @Test
    @DisplayName("HTTP_ERROR answers 500 in SNAP format, then 503 empty, then the rule is used up")
    void httpError() {
        assertThat(addRule("{\"type\":\"HTTP_ERROR\",\"target\":\"CREATE_VA\",\"httpStatus\":500,\"remaining\":1}").status()).isEqualTo(201);
        assertThat(addRule("{\"type\":\"HTTP_ERROR\",\"target\":\"CREATE_VA\",\"httpStatus\":503,\"remaining\":1}").status()).isEqualTo(201);
        assertCode(client.call("POST", CREATE, token, create("1", "E1")), "5002700", "General Error");
        APIResponse unavailable = client.call("POST", CREATE, token, create("1", "E1"));
        assertThat(unavailable.status()).isEqualTo(503);
        assertThat(unavailable.text()).isEmpty();
        assertCode(client.call("POST", CREATE, token, create("1", "E1")), "2002700", "Successful");
        assertThat(SnapTestClient.json(api.get("/portal/api/injections", basicAuth(partner))).size()).isZero();
    }

    @Test
    @DisplayName("TIMEOUT_AFTER_PROCESSING: the client times out, but the VA was created")
    void timeoutAfterProcessing() {
        addRule("{\"type\":\"TIMEOUT_AFTER_PROCESSING\",\"target\":\"CREATE_VA\",\"delayMs\":3000,\"remaining\":1}");
        Map<String, String> headers = client.serviceHeaders("POST", CREATE, token, create("2", "TO-1"));
        RequestOptions options = RequestOptions.create().setMethod("POST").setData(create("2", "TO-1")).setTimeout(1000);
        headers.forEach(options::setHeader);
        assertThatThrownBy(() -> api.fetch(CREATE, options)).isInstanceOf(PlaywrightException.class)
                .hasMessageContaining("Timeout");
        String inquiry = """
                {"partnerServiceId":"%s","customerNo":"2","virtualAccountNo":"%s2","trxId":"TO-1"}"""
                .formatted(partner.partnerServiceId(), partner.partnerServiceId());
        assertCode(client.call("POST", INQUIRY, token, inquiry), "2003000", "Successful");
    }

    @Test
    @DisplayName("SLOW_RESPONSE delays the answer, then processes normally")
    void slowResponse() {
        addRule("{\"type\":\"SLOW_RESPONSE\",\"target\":\"CREATE_VA\",\"delayMs\":1200,\"remaining\":1}");
        long started = System.nanoTime();
        assertCode(client.call("POST", CREATE, token, create("3", "SL-1")), "2002700", "Successful");
        assertThat((System.nanoTime() - started) / 1_000_000).isGreaterThanOrEqualTo(1200);
    }

    @Test
    @DisplayName("Outbound INVALID_SIGNATURE on inquiry is rejected by the partner; no payment is made")
    void invalidSignatureOutbound() {
        addRule("{\"type\":\"INVALID_SIGNATURE\",\"target\":\"INQUIRY\",\"remaining\":1}");
        JsonNode result = biller("10");
        assertThat(result.get("inquiryResponseCode").asString()).isEqualTo("4012400");
        assertThat(app.received("/inquiry").getFirst().signatureValid()).isFalse();
        assertThat(biller("11").get("notificationStatus").asString()).isEqualTo("ACKNOWLEDGED");
    }

    @Test
    @DisplayName("DROP_NOTIFICATION credits without calling the partner; LATE_NOTIFICATION sends it late")
    void dropAndLate() {
        addRule("{\"type\":\"DROP_NOTIFICATION\",\"target\":\"PAYMENT\",\"remaining\":1}");
        assertThat(biller("20").get("notificationStatus").asString()).isEqualTo("DROPPED");
        assertThat(app.received("/payment")).isEmpty();

        addRule("{\"type\":\"LATE_NOTIFICATION\",\"target\":\"PAYMENT\",\"delayMs\":1000,\"remaining\":1}");
        long started = System.nanoTime();
        assertThat(biller("21").get("notificationStatus").asString()).isEqualTo("ACKNOWLEDGED");
        assertThat((System.nanoTime() - started) / 1_000_000).isGreaterThanOrEqualTo(1000);
    }

    @Test
    @DisplayName("Rules are validated: target per type, delay and status only where used; rules can be deleted")
    void validation() {
        assertThat(addRule("{\"type\":\"DROP_NOTIFICATION\",\"target\":\"INQUIRY\",\"remaining\":1}").text())
                .contains("DROP_NOTIFICATION applies to PAYMENT");
        assertThat(addRule("{\"type\":\"SLOW_RESPONSE\",\"target\":\"CREATE_VA\",\"remaining\":1}").text())
                .contains("SLOW_RESPONSE needs delayMs");
        assertThat(addRule("{\"type\":\"HTTP_ERROR\",\"target\":\"CREATE_VA\",\"httpStatus\":404,\"remaining\":1}").text())
                .contains("500, 502 or 503");
        assertThat(addRule("{\"type\":\"HTTP_ERROR\",\"target\":\"CREATE_VA\",\"remaining\":1}").text())
                .contains("needs httpStatus");
        assertThat(addRule("{\"type\":\"DROP_NOTIFICATION\",\"target\":\"PAYMENT\",\"delayMs\":5,\"remaining\":1}").text())
                .contains("takes no delayMs");
        assertThat(addRule("{\"type\":\"INVALID_SIGNATURE\",\"target\":\"PAYMENT\",\"httpStatus\":500,\"remaining\":1}").text())
                .contains("takes no httpStatus");
        assertThat(addRule("{\"type\":\"INVALID_SIGNATURE\",\"target\":\"PAYMENT\"}").status()).isEqualTo(400);

        String id = SnapTestClient.json(addRule("{\"type\":\"INVALID_SIGNATURE\",\"target\":\"PAYMENT\",\"remaining\":3}"))
                .get("id").asString();
        assertThat(api.delete("/portal/api/injections/" + id, basicAuth(partner)).status()).isEqualTo(204);
        assertThat(api.delete("/portal/api/injections/" + id, basicAuth(partner)).status()).isEqualTo(404);
    }
}
