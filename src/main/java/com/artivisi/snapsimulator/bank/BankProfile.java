package com.artivisi.snapsimulator.bank;

import com.artivisi.snapsimulator.dto.OutboundExchange;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.entity.Payment;
import com.artivisi.snapsimulator.enums.Bank;
import com.artivisi.snapsimulator.enums.ChecklistItem;
import com.artivisi.snapsimulator.enums.NotificationStatus;
import com.artivisi.snapsimulator.exception.BusinessException;
import com.artivisi.snapsimulator.snap.SnapService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;

/** Everything that differs between banks: inbound conventions and the bank-to-partner calls. */
public interface BankProfile {

    Bank bank();

    // ---- inbound (partner calls the bank) ----

    SnapService tokenService();

    ObjectNode tokenResponse(String accessToken, int ttlSeconds);

    /** Checks X-PARTNER-ID on service calls; throws SnapException. */
    void checkPartnerIdHeader(String value, BankConnection connection, SnapService svc);

    /** The URL part of the symmetric string-to-sign. */
    String signedPath(String path, String rawQuery);

    int maxCustomerNoDigits();

    List<ChecklistItem> checklist();

    /** Whether the bank offers create-va and the other bank-hosted VA services. */
    boolean bankHostedVa();

    /** X-TIMESTAMP the bank sends, on responses and on its own calls. */
    String timestamp(Instant instant);

    // ---- outbound (bank calls the partner) ----

    List<ChannelOption> channels();

    default ChannelOption channel(String code) {
        return channels().stream().filter(c -> c.code().equals(code)).findFirst()
                .orElseThrow(() -> new BusinessException("channelId", "Unknown channel " + code + " for " + bank()));
    }

    String outboundPartnerId(BankConnection connection);

    String newRequestId(Instant now);

    /** Payment auth code sent with the payment call; null when the bank sends none. */
    String newReferenceNo();

    ObjectNode inquiryBody(BankConnection connection, String customerNo, String virtualAccountNo, ChannelOption channel,
            String requestId, Instant now);

    /** Whether the partner's inquiry answer lets the payment go ahead. */
    boolean inquiryAnswered(OutboundExchange answer);

    /** inquiryAnswer is null for a bank-hosted VA paid without inquiry. */
    String paymentBody(BankConnection connection, Payment payment, String customerNo, String name, ChannelOption channel,
            JsonNode inquiryAnswer);

    /** The stored payment body as sent again on resend. */
    String resendBody(String storedBody);

    NotificationStatus outcome(OutboundExchange answer);
}
