package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import com.artivisi.snapsimulator.support.SnapTestClient;
import com.artivisi.snapsimulator.util.Randoms;
import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

import static com.artivisi.snapsimulator.functional.AccessTokenFunctionalTest.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

@SpecRef("bri.va.number-layout")
class VirtualAccountFunctionalTest extends PlaywrightTestBase {

    private static final String BASE = "/snap/v1.0/transfer-va/";

    private TestPartner partner;
    private SnapTestClient client;
    private String token;

    @BeforeEach
    void partner() {
        partner = signupWithKey(false, 300);
        client = snapClient(partner);
        token = client.obtainToken();
    }

    private String va(String customerNo) {
        return partner.partnerServiceId() + customerNo;
    }

    private String createBody(String customerNo, String trxId, String amount, String extra) {
        return """
                {"partnerServiceId":"%s","customerNo":"%s","virtualAccountNo":"%s","virtualAccountName":"Budi Santoso",
                 "virtualAccountEmail":"budi@example.test","trxId":"%s","totalAmount":{"value":"%s","currency":"IDR"},
                 "billDetails":[{"billCode":"01","billName":"SPP"}]%s}"""
                .formatted(partner.partnerServiceId(), customerNo, va(customerNo), trxId, amount, extra);
    }

    private String keyBody(String customerNo, String trxId) {
        return """
                {"partnerServiceId":"%s","customerNo":"%s","virtualAccountNo":"%s","trxId":"%s"}"""
                .formatted(partner.partnerServiceId(), customerNo, va(customerNo), trxId);
    }

    private APIResponse send(String method, String op, String body) {
        return client.call(method, BASE + op, token, body);
    }

    @Test
    @SpecRef("aspi.va.create-va")
    @SpecRef("aspi.va.create-va#request.virtualAccountName")
    @SpecRef("aspi.va.create-va#request.trxId")
    @SpecRef("aspi.va.create-va#request.totalAmount")
    @SpecRef("aspi.va.inquiry-va")
    @SpecRef("aspi.va.inquiry-va#request.trxId")
    @DisplayName("Create, then inquire: VA data echoes the request, checklist step VA created is stamped")
    void createAndInquire() {
        String expires = SnapTimestamp.formatSeconds(Instant.now().plusSeconds(86_400));
        APIResponse created = send("POST", "create-va", createBody("1234567", "INV-1", "150000.00",
                ",\"expiredDate\":\"" + expires + "\",\"virtualAccountTrxType\":\"C\""));
        JsonNode body = assertCode(created, "2002700", "Successful");
        JsonNode data = body.get("virtualAccountData");
        assertThat(data.get("virtualAccountNo").asString()).isEqualTo(va("1234567")).hasSize(15);
        assertThat(data.get("partnerServiceId").asString()).hasSize(8).startsWith(" ");
        assertThat(data.at("/totalAmount/value").asString()).isEqualTo("150000.00");
        assertThat(data.at("/billDetails/0/billName").asString()).isEqualTo("SPP");
        assertThat(data.get("expiredDate").asString()).isEqualTo(expires);
        assertThat(checklistDone(partner, "VA_CREATED")).isNotNull();

        JsonNode inquired = assertCode(send("POST", "inquiry-va", keyBody("1234567", "INV-1")), "2003000", "Successful");
        assertThat(inquired.at("/virtualAccountData/virtualAccountName").asString()).isEqualTo("Budi Santoso");
        assertThat(inquired.at("/virtualAccountData/lastUpdateDate").asString()).isNotBlank();
    }

    @Test
    @SpecRef("aspi.va.create-va")
    @SpecRef("aspi.va.trx-type")
    @DisplayName("create-va rejects missing fields, bad formats, open VAs, another partner's prefix and duplicates")
    void createValidation() {
        assertCode(send("POST", "create-va", createBody("1", "T1", "100.00", "").replace("\"trxId\":\"T1\",", "")),
                "4002702", "Invalid Mandatory Field trxId");
        assertCode(send("POST", "create-va", createBody("1", "T1", "100", "")),
                "4002701", "Invalid Field Format totalAmount.value");
        assertCode(send("POST", "create-va", createBody("1", "T1", "100.00", ",\"virtualAccountTrxType\":\"O\"")),
                "4002701", "Invalid Field Format virtualAccountTrxType");
        assertCode(send("POST", "create-va", createBody("12345678901234", "T1", "100.00", "")),
                "4002701", "Invalid Field Format customerNo");
        assertCode(send("POST", "create-va", createBody("1", "T1", "100.00", "")
                        .replace("\"virtualAccountNo\":\"" + va("1") + "\"", "\"virtualAccountNo\":\"" + va("2") + "\"")),
                "4002701", "Invalid Field Format virtualAccountNo");
        assertCode(send("POST", "create-va", createBody("1", "T1", "100.00", ",\"expiredDate\":\"2020-01-01T00:00:00+07:00\"")),
                "4002701", "Invalid Field Format expiredDate");
        String otherPrefix = createBody("1", "T1", "100.00", "").replace(partner.partnerServiceId(), "   99999");
        assertCode(send("POST", "create-va", otherPrefix), "4042716", "Partner Not Found");
        assertCode(send("POST", "create-va", "[]"), "4002700", "Bad Request");

        assertCode(send("POST", "create-va", createBody("1", "T1", "100.00", "")), "2002700", "Successful");
        assertCode(send("POST", "create-va", createBody("1", "T2", "100.00", "")), "4092701", "Duplicate partnerReferenceNo");
        assertCode(send("POST", "create-va", createBody("2", "T1", "100.00", "")), "4092701", "Duplicate partnerReferenceNo");
    }

    @Test
    @SpecRef("aspi.va.update-va")
    @SpecRef("aspi.va.update-va#request.trxId")
    @DisplayName("update-va changes name and amount; wrong trxId or unknown VA returns 404xx12")
    void update() {
        assertCode(send("POST", "create-va", createBody("77", "UPD-1", "100.00", "")), "2002700", "Successful");
        String update = createBody("77", "UPD-1", "250000.00", "").replace("Budi Santoso", "Budi S.");
        JsonNode updated = assertCode(send("PUT", "update-va", update), "2002800", "Successful");
        assertThat(updated.at("/virtualAccountData/virtualAccountName").asString()).isEqualTo("Budi S.");
        assertThat(updated.at("/virtualAccountData/totalAmount/value").asString()).isEqualTo("250000.00");

        assertCode(send("PUT", "update-va", update.replace("UPD-1", "OTHER")),
                "4042812", "Invalid Bill/Virtual Account trxId mismatch");
        assertCode(send("PUT", "update-va", createBody("78", "X", "1.00", "")),
                "4042812", "Invalid Bill/Virtual Account Not Found");
        assertCode(send("POST", "inquiry-va", keyBody("79", "X")), "4043012", "Invalid Bill/Virtual Account Not Found");
    }

    @Test
    @SpecRef("aspi.va.delete-va")
    @DisplayName("delete-va (DELETE with a JSON body) removes the VA; the number can be created again")
    void delete() {
        assertCode(send("POST", "create-va", createBody("55", "DEL-1", "100.00", "")), "2002700", "Successful");
        JsonNode deleted = assertCode(send("DELETE", "delete-va", keyBody("55", "DEL-1")), "2003100", "Successful");
        assertThat(deleted.at("/virtualAccountData/trxId").asString()).isEqualTo("DEL-1");
        assertCode(send("POST", "inquiry-va", keyBody("55", "DEL-1")), "4043012", "Invalid Bill/Virtual Account Not Found");
        assertCode(send("POST", "create-va", createBody("55", "DEL-2", "100.00", "")), "2002700", "Successful");
    }

    @Test
    @SpecRef("aspi.va.inquiry-status")
    @DisplayName("inquiry status: unpaid VA returns 4042601, unknown VA 4042612")
    void statusUnpaid() {
        assertCode(send("POST", "create-va", createBody("66", "ST-1", "100.00", "")), "2002700", "Successful");
        String status = """
                {"partnerServiceId":"%s","customerNo":"66","virtualAccountNo":"%s","inquiryRequestId":"%s"}"""
                .formatted(partner.partnerServiceId(), va("66"), Randoms.digits(20));
        assertCode(send("POST", "status", status), "4042601", "Transaction Not Found");
        assertCode(send("POST", "status", status.replace(va("66"), va("67")).replace("\"66\"", "\"67\"")),
                "4042612", "Invalid Bill/Virtual Account Not Found");
    }
}
