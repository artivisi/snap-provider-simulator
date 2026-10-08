package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.config.BankKeys;
import com.artivisi.snapsimulator.enums.Bank;
import com.artivisi.snapsimulator.support.FakePartnerApp;
import com.artivisi.snapsimulator.support.SnapTestClient;
import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static com.artivisi.snapsimulator.functional.AccessTokenFunctionalTest.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

/** A BCA connection: the bank's inquiry and payment flag to an app under /openapi, and the partner's status call. */
class BcaFunctionalTest extends PlaywrightTestBase {

    private static final String STATUS = "/openapi/v2.0/transfer-va/status";

    @Autowired
    BankKeys bankKeys;

    private FakePartnerApp app;
    private TestPartner partner;

    @BeforeEach
    void setUp() throws Exception {
        app = new FakePartnerApp("bca-bank-id", "bca-bank-secret", bankKeys.of(Bank.BCA).publicKey(), "/partner/openapi");
        partner = signupWithKey(Bank.BCA, true, 900);
        assertThat(api.put(conn(partner, "/endpoint"), json(partner).setData("{\"baseUrl\":\"" + app.baseUrl()
                + "\",\"clientId\":\"bca-bank-id\",\"clientSecret\":\"bca-bank-secret\"}")).status()).isEqualTo(204);
    }

    @AfterEach
    void tearDown() {
        app.close();
    }

    private String va(String customerNo) {
        return partner.partnerServiceId() + customerNo;
    }

    private JsonNode pay(String customerNo, String amount, String channel) {
        APIResponse response = api.post(conn(partner, "/biller-payments"), json(partner).setData(
                "{\"virtualAccountNo\":\"" + va(customerNo) + "\",\"amount\":\"" + amount + "\",\"channelId\":\"" + channel + "\"}"));
        assertThat(response.status()).as(response.text()).isEqualTo(200);
        return SnapTestClient.json(response);
    }

    private String statusBody(String customerNo, String inquiryRequestId) {
        return """
                {"partnerServiceId":"%s","customerNo":"%s","virtualAccountNo":"%s","inquiryRequestId":"%s","additionalInfo":{}}"""
                .formatted(partner.partnerServiceId(), customerNo, va(customerNo), inquiryRequestId);
    }

    @Test
    @SpecRef("bca.va.inquiry")
    @SpecRef("bca.va.inquiry#request.inquiryRequestId")
    @SpecRef("bca.va.inquiry#request.channelCode")
    @SpecRef("bca.va.inquiry#request.trxDateInit")
    @SpecRef("bca.va.payment")
    @SpecRef("bca.va.payment#request.paymentRequestId")
    @SpecRef("bca.va.payment#request.referenceNo")
    @SpecRef("bca.va.payment#request.flagAdvise")
    @SpecRef("bca.va.payment#request.billDetails[].billReferenceNo")
    @SpecRef("bca.headers.service")
    @SpecRef("bca.channel-code")
    @SpecRef("bca.va.number-layout")
    @DisplayName("BCA inquiry and payment flag: headers, request ids, channel code, bills echoed with reference numbers")
    void inquiryAndPaymentFlag() {
        String customerNo = "123456789012345678";
        JsonNode result = pay(customerNo, "250000.00", "6011");
        assertThat(result.get("inquiryResponseCode").asString()).isEqualTo("2002400");
        assertThat(result.get("notificationStatus").asString()).isEqualTo("ACKNOWLEDGED");

        FakePartnerApp.Received token = app.received("/access-token/b2b").getFirst();
        FakePartnerApp.Received inquiry = app.received("/inquiry").getFirst();
        FakePartnerApp.Received payment = app.received("/payment").getFirst();
        assertThat(token.signatureValid()).isTrue();
        assertThat(token.path()).isEqualTo("/partner/openapi/v1.0/access-token/b2b");
        assertThat(inquiry.signatureValid()).isTrue();
        assertThat(payment.signatureValid()).isTrue();
        for (FakePartnerApp.Received call : new FakePartnerApp.Received[] {inquiry, payment}) {
            assertThat(call.headers()).containsEntry("channel-id", "95231")
                    .containsEntry("x-partner-id", partner.partnerServiceId().trim());
            assertThat(call.headers().get("x-timestamp")).hasSize(25);
        }
        assertThat(inquiry.body().get("virtualAccountNo").asString()).isEqualTo(va(customerNo)).hasSize(26);
        assertThat(inquiry.body().get("channelCode").asInt()).isEqualTo(6011);
        assertThat(inquiry.body().get("inquiryRequestId").asString()).matches("\\d{30}");
        assertThat(inquiry.body().get("amount").isNull()).isTrue();
        assertThat(inquiry.body().get("sourceBankCode").asString()).isEqualTo("014");

        JsonNode flag = payment.body();
        assertThat(flag.get("paymentRequestId").asString()).isEqualTo(inquiry.body().get("inquiryRequestId").asString());
        assertThat(flag.get("flagAdvise").asString()).isEqualTo("N");
        assertThat(flag.get("referenceNo").asString()).matches("\\d{11}");
        assertThat(flag.at("/paidAmount/value").asString()).isEqualTo("250000.00");
        assertThat(flag.at("/totalAmount/value").asString()).isEqualTo("250000.00");
        assertThat(flag.get("subCompany").asString()).isEqualTo("00000");
        assertThat(flag.at("/billDetails/0/billNo").asString()).isEqualTo("B-1");
        assertThat(flag.at("/billDetails/0/billReferenceNo").asString()).isEqualTo(flag.get("referenceNo").asString());
        assertThat(flag.get("virtualAccountName").asString()).isEqualTo("Siti Aminah");
        assertThat(checklistDone(partner, "PAYMENT_ACKNOWLEDGED")).isNotNull();
    }

    @Test
    @SpecRef("bca.inquiry-status")
    @DisplayName("Inquiry answered 2002400 but inquiryStatus 01 is refused: no payment")
    void inquiryStatusRefused() {
        app.answerInquiry(body -> new FakePartnerApp.Answer(200, FakePartnerApp.inquirySuccess(body)
                .replace("\"inquiryStatus\":\"00\"", "\"inquiryStatus\":\"01\"")));
        JsonNode result = pay("55", "1000.00", "6014");
        assertThat(result.get("paymentId").isNull()).isTrue();
        assertThat(app.received("/payment")).isEmpty();
    }

    @Test
    @SpecRef("bca.payment-flag-status")
    @SpecRef("bca.va.payment#response.virtualAccountData.paymentFlagStatus")
    @DisplayName("Payment answer: 4042518 with flag 00 acknowledges, flag 01 reverses, flag 02 suspends, other codes reverse")
    void paymentOutcomes() {
        app.answerPayment(b -> new FakePartnerApp.Answer(404, FakePartnerApp.paymentReply(b, "4042518", "00")));
        assertThat(pay("61", "10.00", "6011").get("notificationStatus").asString()).isEqualTo("ACKNOWLEDGED");
        app.answerPayment(b -> new FakePartnerApp.Answer(200, FakePartnerApp.paymentReply(b, "2002500", "01")));
        assertThat(pay("62", "10.00", "6011").get("notificationStatus").asString()).isEqualTo("REVERSED");
        app.answerPayment(b -> new FakePartnerApp.Answer(202, FakePartnerApp.paymentReply(b, "2022500", "02")));
        assertThat(pay("63", "10.00", "6011").get("notificationStatus").asString()).isEqualTo("SUSPENDED");
        app.answerPayment(b -> new FakePartnerApp.Answer(500, "{\"responseCode\":\"5002500\",\"responseMessage\":\"x\"}"));
        assertThat(pay("64", "10.00", "6011").get("notificationStatus").asString()).isEqualTo("REVERSED");
    }

    @Test
    @SpecRef("sim.biller-payment.resend")
    @SpecRef("bca.va.payment#request.flagAdvise")
    @DisplayName("Resend repeats the flag with the same X-EXTERNAL-ID and paymentRequestId, flagAdvise Y")
    void resend() {
        String paymentId = pay("71", "5000.00", "6017").get("paymentId").asString();
        JsonNode resent = SnapTestClient.json(api.post(conn(partner, "/payments/" + paymentId + "/resend"), basicAuth(partner)));
        assertThat(resent.get("paymentResponseCode").asString()).isEqualTo("4092500");
        FakePartnerApp.Received first = app.received("/payment").get(0);
        FakePartnerApp.Received second = app.received("/payment").get(1);
        assertThat(second.headers().get("x-external-id")).isEqualTo(first.headers().get("x-external-id"));
        assertThat(second.body().get("paymentRequestId").asString()).isEqualTo(first.body().get("paymentRequestId").asString());
        assertThat(second.body().get("flagAdvise").asString()).isEqualTo("Y");
        assertThat(second.signatureValid()).isTrue();
    }

    @Test
    @SpecRef("bca.va.inquiry-status")
    @SpecRef("bca.va.inquiry-status#request.inquiryRequestId")
    @SpecRef("bca.va.inquiry-status#response.virtualAccountData.paymentFlagStatus")
    @DisplayName("Partner's status call at /openapi/v2.0 returns the payment flag; unknown ids return 4042601")
    void inquiryStatusCall() {
        pay("81", "75000.00", "6011");
        String inquiryRequestId = app.received("/inquiry").getFirst().body().get("inquiryRequestId").asString();
        SnapTestClient client = snapClient(partner);
        String token = client.obtainToken();

        JsonNode body = assertCode(client.call("POST", STATUS, token, statusBody("81", inquiryRequestId)), "2002600", "Success");
        JsonNode data = body.get("virtualAccountData");
        assertThat(data.get("paymentFlagStatus").asString()).isEqualTo("00");
        assertThat(data.get("paymentRequestId").asString()).isEqualTo(inquiryRequestId);
        assertThat(data.at("/paidAmount/value").asString()).isEqualTo("75000.00");
        assertThat(data.get("referenceNo").asString()).matches("\\d{11}");
        assertThat(data.at("/billDetails/0/status").asString()).isEqualTo("00");

        assertCode(client.call("POST", STATUS, token, statusBody("81", "999999999999999999999999999999")),
                "4042601", "Transaction Not Found");
        assertCode(client.call("POST", STATUS, token, statusBody("81", "").replace(",\"inquiryRequestId\":\"\"", "")),
                "4002602", "Invalid Mandatory Field inquiryRequestId");
    }

    @Test
    @SpecRef("bca.headers.service")
    @DisplayName("BCA service calls need X-PARTNER-ID = company code; a BRI token is not valid at BCA")
    void headers() {
        SnapTestClient client = snapClient(partner);
        String token = client.obtainToken();
        String body = statusBody("1", "1");
        Map<String, String> headers = client.serviceHeaders("POST", STATUS, token, body);
        headers.put("X-PARTNER-ID", partner.clientId());
        assertCode(client.call("POST", STATUS, headers, body), "4002601", "Invalid Field Format X-PARTNER-ID");

        TestPartner bri = addConnection(partner.email(), partner.password(), Bank.BRI, false, 300);
        String briToken = snapClient(bri).obtainToken();
        assertCode(client.call("POST", STATUS, briToken, body), "4012601", "Invalid Token (B2B)");
    }
}
