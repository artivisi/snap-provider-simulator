package com.artivisi.snapsimulator.bank;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.OutboundExchange;
import com.artivisi.snapsimulator.enums.ChecklistItem;
import com.artivisi.snapsimulator.enums.NotificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BcaProfileTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final BcaProfile profile = new BcaProfile(json);

    private OutboundExchange answer(Integer status, String body) {
        return new OutboundExchange(null, status, body, body == null ? null : json.readTree(body), null);
    }

    @Test
    @SpecRef("bca.sig.relative-url")
    @DisplayName("Relative URL keeps the path and sorts query parameters by name, then value")
    void relativeUrl() {
        assertThat(profile.signedPath("/openapi/v2.0/transfer-va/status", null)).isEqualTo("/openapi/v2.0/transfer-va/status");
        assertThat(profile.signedPath("/api/v2/sample", "Z-param=value2&A-param=value1&B-param=value3"))
                .isEqualTo("/api/v2/sample?A-param=value1&B-param=value3&Z-param=value2");
        assertThat(profile.signedPath("/x", "b=2&a=2&a=1")).isEqualTo("/x?a=1&a=2&b=2");
    }

    @Test
    @SpecRef("bca.payment-flag-status")
    @DisplayName("No answer suspends; unknown flag after a flag code suspends")
    void outcomeEdges() {
        assertThat(profile.outcome(answer(null, null))).isEqualTo(NotificationStatus.SUSPENDED);
        assertThat(profile.outcome(answer(200, "{\"responseCode\":\"2002500\",\"virtualAccountData\":{}}")))
                .isEqualTo(NotificationStatus.SUSPENDED);
    }

    @Test
    @SpecRef("bca.inquiry-status")
    @DisplayName("Inquiry needs 2002400/2022400 and inquiryStatus 00")
    void inquiryAnswered() {
        assertThat(profile.inquiryAnswered(answer(202, "{\"responseCode\":\"2022400\",\"virtualAccountData\":{\"inquiryStatus\":\"00\"}}"))).isTrue();
        assertThat(profile.inquiryAnswered(answer(200, "{\"responseCode\":\"2002400\",\"virtualAccountData\":{}}"))).isFalse();
        assertThat(profile.inquiryAnswered(answer(200, "{\"responseCode\":\"2002401\",\"virtualAccountData\":{\"inquiryStatus\":\"00\"}}"))).isFalse();
    }

    @Test
    @SpecRef("bca.va.number-layout")
    @DisplayName("Request ids are 30 digits starting with the Jakarta date-time; no VA-created step")
    void idsAndChecklist() {
        assertThat(profile.newRequestId(Instant.parse("2026-10-08T03:04:05Z"))).startsWith("20261008100405").hasSize(30);
        assertThat(profile.newReferenceNo()).matches("\\d{11}");
        assertThat(profile.maxCustomerNoDigits()).isEqualTo(18);
        assertThat(profile.checklist()).doesNotContain(ChecklistItem.VA_CREATED).hasSize(6);
        assertThat(profile.bankHostedVa()).isFalse();
    }
}
