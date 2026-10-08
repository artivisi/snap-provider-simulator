package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.config.BankKeys;
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

import static com.artivisi.snapsimulator.functional.AccessTokenFunctionalTest.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

class BankToPartnerFunctionalTest extends PlaywrightTestBase {

    private static final String BANK_CLIENT_ID = "bank-client-01";
    private static final String BANK_CLIENT_SECRET = "bank-secret-for-hmac";

    @Autowired
    BankKeys bankKeys;

    private FakePartnerApp app;
    private TestPartner partner;

    @BeforeEach
    void startApp() throws Exception {
        app = new FakePartnerApp(BANK_CLIENT_ID, BANK_CLIENT_SECRET, bankKeys.publicKey());
        partner = signupWithKey(false, 300);
        registerEndpoint(BANK_CLIENT_ID);
    }

    @AfterEach
    void stopApp() {
        app.close();
    }

    private void registerEndpoint(String clientId) {
        APIResponse response = api.put("/portal/api/endpoint", basicAuth(partner)
                .setHeader("Content-Type", "application/json")
                .setData("{\"baseUrl\":\"" + app.baseUrl() + "\",\"clientId\":\"" + clientId + "\",\"clientSecret\":\""
                        + BANK_CLIENT_SECRET + "\"}"));
        assertThat(response.status()).isEqualTo(204);
    }

    private JsonNode post(String path, String body) {
        APIResponse response = api.post(path, basicAuth(partner).setHeader("Content-Type", "application/json").setData(body));
        assertThat(response.status()).as(response.text()).isEqualTo(200);
        return SnapTestClient.json(response);
    }

    private JsonNode billerPayment(String customerNo, String amount) {
        return post("/portal/api/biller-payments", "{\"virtualAccountNo\":\"" + partner.partnerServiceId() + customerNo
                + "\",\"amount\":\"" + amount + "\",\"channelId\":\"00002\"}");
    }

    @Test
    @SpecRef("aspi.oauth.token-b2b-outbound")
    @SpecRef("aspi.oauth.token-b2b-outbound#request.grantType")
    @SpecRef("aspi.oauth.token-b2b-outbound#response.accessToken")
    @SpecRef("aspi.oauth.token-b2b-outbound#response.expiresIn")
    @SpecRef("sim.portal.endpoint")
    @DisplayName("Test connection: the bank's RSA-signed token request is accepted and the checklist is stamped")
    void testConnection() {
        JsonNode result = post("/portal/api/endpoint/test", "");
        assertThat(result.get("reachable").asBoolean()).isTrue();
        assertThat(result.get("httpStatus").asInt()).isEqualTo(200);
        FakePartnerApp.Received token = app.received("/access-token/b2b").getFirst();
        assertThat(token.signatureValid()).isTrue();
        assertThat(token.body().get("grantType").asString()).isEqualTo("client_credentials");
        assertThat(checklistDone(partner, "ENDPOINT_REACHABLE")).isNotNull();

        registerEndpoint("wrong-client");
        JsonNode refused = post("/portal/api/endpoint/test", "");
        assertThat(refused.get("reachable").asBoolean()).isFalse();
        assertThat(refused.get("httpStatus").asInt()).isEqualTo(401);
    }

    @Test
    @SpecRef("sim.biller-payment.trigger")
    @SpecRef("bri.briva-online.inquiry")
    @SpecRef("bri.briva-online.inquiry#request.inquiryRequestId")
    @SpecRef("bri.briva-online.inquiry#request.channelCode")
    @SpecRef("bri.briva-online.inquiry#request.sourceBankCode")
    @SpecRef("bri.briva-online.payment")
    @SpecRef("bri.briva-online.payment#request.paidAmount")
    @SpecRef("bri.briva-online.payment#request.paymentRequestId")
    @SpecRef("bri.channel-id")
    @SpecRef("snap.sig.symmetric")
    @DisplayName("Customer payment: signed inquiry, then payment; acknowledged with flag 00; checklist stamped")
    void billerPaymentAcknowledged() {
        JsonNode result = billerPayment("8812345", "250000.00");
        assertThat(result.get("inquiryResponseCode").asString()).isEqualTo("2002400");
        assertThat(result.get("paymentResponseCode").asString()).isEqualTo("2002500");
        assertThat(result.get("notificationStatus").asString()).isEqualTo("ACKNOWLEDGED");

        FakePartnerApp.Received inquiry = app.received("/inquiry").getFirst();
        FakePartnerApp.Received payment = app.received("/payment").getFirst();
        assertThat(inquiry.signatureValid()).isTrue();
        assertThat(payment.signatureValid()).isTrue();
        assertThat(inquiry.headers()).containsEntry("x-partner-id", BANK_CLIENT_ID).containsEntry("channel-id", "00002");
        assertThat(inquiry.headers().get("x-external-id")).matches("\\d{32}");
        assertThat(inquiry.body().get("channelCode").asInt()).isEqualTo(2);
        assertThat(inquiry.body().get("sourceBankCode").asString()).isEqualTo("002");
        assertThat(inquiry.body().at("/amount/value").asString()).isEqualTo("0.00");
        assertThat(payment.body().get("paymentRequestId").asString()).isEqualTo(inquiry.body().get("inquiryRequestId").asString());
        assertThat(payment.body().at("/paidAmount/value").asString()).isEqualTo("250000.00");
        assertThat(payment.body().get("virtualAccountName").asString()).isEqualTo("Siti Aminah");
        assertThat(payment.body().has("trxId")).isFalse();
        assertThat(checklistDone(partner, "INQUIRY_ANSWERED")).isNotNull();
        assertThat(checklistDone(partner, "PAYMENT_ACKNOWLEDGED")).isNotNull();
    }

    @Test
    @DisplayName("Inquiry refused by the partner: no payment call, no payment recorded")
    void inquiryRefused() {
        app.answerInquiry(body -> new FakePartnerApp.Answer(404,
                "{\"responseCode\":\"4042412\",\"responseMessage\":\"Invalid Bill/Virtual Account\"}"));
        JsonNode result = billerPayment("1", "1000.00");
        assertThat(result.get("inquiryResponseCode").asString()).isEqualTo("4042412");
        assertThat(result.get("paymentId").isNull()).isTrue();
        assertThat(app.received("/payment")).isEmpty();
        assertThat(SnapTestClient.json(api.get("/portal/api/payments", basicAuth(partner))).size()).isZero();
    }

    @Test
    @SpecRef("bri.payment-flag-status")
    @SpecRef("bri.briva-online.payment#response.virtualAccountData.paymentFlagStatus")
    @DisplayName("Payment outcome: flag 01 reverses, flag 02 and HTTP 500 suspend, listed 4xx reverses")
    void paymentOutcomes() {
        app.answerPayment(body -> new FakePartnerApp.Answer(200, FakePartnerApp.paymentReply(body, "2002500", "01")));
        assertThat(billerPayment("11", "10.00").get("notificationStatus").asString()).isEqualTo("REVERSED");
        app.answerPayment(body -> new FakePartnerApp.Answer(200, FakePartnerApp.paymentReply(body, "2002500", "02")));
        assertThat(billerPayment("12", "10.00").get("notificationStatus").asString()).isEqualTo("SUSPENDED");
        app.answerPayment(body -> new FakePartnerApp.Answer(500, "{\"responseCode\":\"5002500\",\"responseMessage\":\"General Error\"}"));
        assertThat(billerPayment("13", "10.00").get("notificationStatus").asString()).isEqualTo("SUSPENDED");
        app.answerPayment(body -> new FakePartnerApp.Answer(404, "{\"responseCode\":\"4042514\",\"responseMessage\":\"Paid Bill\"}"));
        assertThat(billerPayment("14", "10.00").get("notificationStatus").asString()).isEqualTo("REVERSED");
        app.answerPayment(body -> new FakePartnerApp.Answer(200, "not json"));
        assertThat(billerPayment("15", "10.00").get("notificationStatus").asString()).isEqualTo("SUSPENDED");
    }

    @Test
    @SpecRef("sim.biller-payment.resend")
    @DisplayName("Resend repeats the payment call with the same X-EXTERNAL-ID and body")
    void resend() {
        JsonNode result = billerPayment("21", "75000.00");
        String paymentId = result.get("paymentId").asString();
        JsonNode resent = post("/portal/api/biller-payments/" + paymentId + "/resend", "");
        assertThat(resent.get("paymentResponseCode").asString()).isEqualTo("4092500");
        assertThat(resent.get("message").asString()).contains("Resent with X-EXTERNAL-ID");

        FakePartnerApp.Received first = app.received("/payment").get(0);
        FakePartnerApp.Received second = app.received("/payment").get(1);
        assertThat(second.headers().get("x-external-id")).isEqualTo(first.headers().get("x-external-id"));
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(second.signatureValid()).isTrue();
    }

    @Test
    @SpecRef("sim.va.pay")
    @SpecRef("bri.briva-online.payment#request.trxId")
    @SpecRef("aspi.va.inquiry-status")
    @DisplayName("Paying a bank-hosted VA notifies the partner with trxId; status then answers paid; update returns Paid Bill")
    void bankHostedPay() {
        SnapTestClient client = snapClient(partner);
        String token = client.obtainToken();
        String va = partner.partnerServiceId() + "500";
        String create = """
                {"partnerServiceId":"%s","customerNo":"500","virtualAccountNo":"%s","virtualAccountName":"Andi",
                 "trxId":"BH-1","totalAmount":{"value":"120000.00","currency":"IDR"}}""".formatted(partner.partnerServiceId(), va);
        assertCode(client.call("POST", "/snap/v1.0/transfer-va/create-va", token, create), "2002700", "Successful");

        JsonNode paid = post("/portal/api/va-payments", "{\"virtualAccountNo\":\"" + va + "\",\"channelId\":\"00003\"}");
        assertThat(paid.get("notificationStatus").asString()).isEqualTo("ACKNOWLEDGED");
        FakePartnerApp.Received notification = app.received("/payment").getFirst();
        assertThat(notification.body().get("trxId").asString()).isEqualTo("BH-1");
        assertThat(notification.body().at("/paidAmount/value").asString()).isEqualTo("120000.00");

        String status = """
                {"partnerServiceId":"%s","customerNo":"500","virtualAccountNo":"%s","inquiryRequestId":"Q1"}"""
                .formatted(partner.partnerServiceId(), va);
        JsonNode statusBody = assertCode(client.call("POST", "/snap/v1.0/transfer-va/status", token, status),
                "2002600", "Successful");
        assertThat(statusBody.at("/virtualAccountData/paymentFlagStatus").asString()).isEqualTo("00");
        assertThat(statusBody.at("/virtualAccountData/paidAmount/value").asString()).isEqualTo("120000.00");
        assertThat(statusBody.at("/virtualAccountData/paymentRequestId").asString())
                .isEqualTo(notification.body().get("paymentRequestId").asString());

        String update = create.replace("Andi", "Andi B");
        assertCode(client.call("PUT", "/snap/v1.0/transfer-va/update-va", token, update), "4042814", "Paid Bill");
        assertCode(client.call("DELETE", "/snap/v1.0/transfer-va/delete-va", token, update), "4043114", "Paid Bill");

        APIResponse again = api.post("/portal/api/va-payments", basicAuth(partner).setHeader("Content-Type", "application/json")
                .setData("{\"virtualAccountNo\":\"" + va + "\",\"channelId\":\"00003\"}"));
        assertThat(again.status()).isEqualTo(400);
        assertThat(again.text()).contains("already paid");
    }

    @Test
    @DisplayName("Without a partner endpoint, a bank-hosted payment is recorded and not notified")
    void payWithoutEndpoint() {
        assertThat(api.delete("/portal/api/endpoint", basicAuth(partner)).status()).isEqualTo(204);
        SnapTestClient client = snapClient(partner);
        String token = client.obtainToken();
        String va = partner.partnerServiceId() + "600";
        client.call("POST", "/snap/v1.0/transfer-va/create-va", token, """
                {"partnerServiceId":"%s","customerNo":"600","virtualAccountNo":"%s","virtualAccountName":"Rina",
                 "trxId":"NE-1","totalAmount":{"value":"5000.00","currency":"IDR"}}""".formatted(partner.partnerServiceId(), va));
        JsonNode paid = post("/portal/api/va-payments", "{\"virtualAccountNo\":\"" + va + "\",\"channelId\":\"00001\"}");
        assertThat(paid.get("notificationStatus").asString()).isEqualTo("NOT_NOTIFIED");
        assertThat(app.received()).isEmpty();

        APIResponse biller = api.post("/portal/api/biller-payments", basicAuth(partner).setHeader("Content-Type", "application/json")
                .setData("{\"virtualAccountNo\":\"" + partner.partnerServiceId() + "1\",\"amount\":\"1.00\",\"channelId\":\"00001\"}"));
        assertThat(SnapTestClient.json(biller).get("message").asString()).contains("No partner endpoint registered");
    }

    @Test
    @DisplayName("Biller payment input is validated: VA prefix, channel list, amount format")
    void billerValidation() {
        RequestOptions base = basicAuth(partner).setHeader("Content-Type", "application/json");
        APIResponse wrongPrefix = api.post("/portal/api/biller-payments", base.setData(
                "{\"virtualAccountNo\":\"   99999123\",\"amount\":\"1.00\",\"channelId\":\"00001\"}"));
        assertThat(wrongPrefix.status()).isEqualTo(400);
        assertThat(wrongPrefix.text()).contains("partnerServiceId");
        APIResponse badChannel = api.post("/portal/api/biller-payments", basicAuth(partner).setHeader("Content-Type", "application/json")
                .setData("{\"virtualAccountNo\":\"" + partner.partnerServiceId() + "1\",\"amount\":\"1.00\",\"channelId\":\"12345\"}"));
        assertThat(badChannel.text()).contains("Unknown channel 12345");
        APIResponse badAmount = api.post("/portal/api/biller-payments", basicAuth(partner).setHeader("Content-Type", "application/json")
                .setData("{\"virtualAccountNo\":\"" + partner.partnerServiceId() + "1\",\"amount\":\"1\",\"channelId\":\"00001\"}"));
        assertThat(SnapTestClient.json(badAmount).at("/fieldErrors/amount").asString()).contains("2 places");
    }
}
