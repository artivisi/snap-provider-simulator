package com.artivisi.snapsimulator.bank;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.OutboundExchange;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.entity.Payment;
import com.artivisi.snapsimulator.enums.Bank;
import com.artivisi.snapsimulator.enums.ChecklistItem;
import com.artivisi.snapsimulator.enums.NotificationStatus;
import com.artivisi.snapsimulator.exception.SnapException;
import com.artivisi.snapsimulator.snap.SnapService;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** BRI: public OAuth and BRIVA Online docs; bank-hosted VA from the public ASPI standard. */
@Component
public class BriProfile implements BankProfile {

    private static final Pattern PARTNER_ID_FORMAT = Pattern.compile("[A-Za-z0-9]{1,36}");
    private static final Pattern LISTED_PAYMENT_4XX = Pattern.compile("4(0[0-9]|1[0-9])25\\d\\d");
    private static final String SOURCE_BANK_CODE = "002";

    @SpecRef("bri.channel-id")
    private static final List<ChannelOption> CHANNELS = List.of(
            channel("00001", "Teller"), channel("00002", "ATM"), channel("00003", "Internet / mobile banking"),
            channel("00004", "SMS banking"), channel("00005", "Cash management"), channel("00006", "EDC"),
            channel("00007", "RTGS"), channel("00008", "Other"), channel("00009", "API"));

    private final JsonMapper json;

    public BriProfile(JsonMapper json) {
        this.json = json;
    }

    /** CHANNEL-ID from the list; channelCode is its integer value (A28). */
    private static ChannelOption channel(String id, String label) {
        return new ChannelOption(id, label, id, Integer.parseInt(id));
    }

    @Override
    public Bank bank() {
        return Bank.BRI;
    }

    @Override
    public SnapService tokenService() {
        return SnapService.BRI_ACCESS_TOKEN;
    }

    /** No responseCode on success (A12). */
    @Override
    @SpecRef("bri.oauth.token-b2b#response.tokenType")
    public ObjectNode tokenResponse(String accessToken, int ttlSeconds) {
        ObjectNode body = json.createObjectNode();
        body.put("accessToken", accessToken);
        body.put("tokenType", "BearerToken");
        body.put("expiresIn", String.valueOf(ttlSeconds));
        return body;
    }

    /** Format only (A10). */
    @Override
    @SpecRef("snap.headers.service")
    public void checkPartnerIdHeader(String value, BankConnection connection, SnapService svc) {
        if (!PARTNER_ID_FORMAT.matcher(value).matches()) {
            throw new SnapException(svc.invalidFieldFormat("X-PARTNER-ID"));
        }
    }

    /** Path without query string (A3). */
    @Override
    @SpecRef("snap.sig.symmetric")
    public String signedPath(String path, String rawQuery) {
        return path;
    }

    @Override
    @SpecRef("bri.va.number-layout")
    public int maxCustomerNoDigits() {
        return 13;
    }

    @Override
    public List<ChecklistItem> checklist() {
        return Arrays.asList(ChecklistItem.values());
    }

    @Override
    public boolean bankHostedVa() {
        return true;
    }

    /** With milliseconds (A7). */
    @Override
    public String timestamp(Instant instant) {
        return SnapTimestamp.format(instant);
    }

    @Override
    public List<ChannelOption> channels() {
        return CHANNELS;
    }

    /** The client id the partner issued to the bank (A28). */
    @Override
    public String outboundPartnerId(BankConnection connection) {
        return connection.getEndpointClientId();
    }

    @Override
    public String newRequestId(Instant now) {
        return UUID.randomUUID().toString();
    }

    @Override
    public String newReferenceNo() {
        return null;
    }

    /** A29: inquiry amount 0.00 IDR, sourceBankCode 002. */
    @Override
    @SpecRef("bri.briva-online.inquiry")
    @SpecRef("bri.briva-online.inquiry#request.inquiryRequestId")
    @SpecRef("bri.briva-online.inquiry#request.channelCode")
    @SpecRef("bri.briva-online.inquiry#request.sourceBankCode")
    public ObjectNode inquiryBody(BankConnection connection, String customerNo, String virtualAccountNo,
            ChannelOption channel, String requestId, Instant now) {
        ObjectNode inquiry = json.createObjectNode();
        inquiry.put("partnerServiceId", connection.getPartnerServiceId());
        inquiry.put("customerNo", customerNo);
        inquiry.put("virtualAccountNo", virtualAccountNo);
        ObjectNode zero = inquiry.putObject("amount");
        zero.put("value", "0.00");
        zero.put("currency", "IDR");
        inquiry.put("trxDateInit", SnapTimestamp.formatSeconds(now));
        inquiry.put("channelCode", channel.channelCode());
        inquiry.put("sourceBankCode", SOURCE_BANK_CODE);
        inquiry.put("inquiryRequestId", requestId);
        return inquiry;
    }

    @Override
    public boolean inquiryAnswered(OutboundExchange answer) {
        String code = answer.responseCode();
        return answer.status() != null && code != null && code.startsWith("2");
    }

    @Override
    @SpecRef("bri.briva-online.payment")
    @SpecRef("bri.briva-online.payment#request.paidAmount")
    @SpecRef("bri.briva-online.payment#request.paymentRequestId")
    @SpecRef("bri.briva-online.payment#request.trxId")
    public String paymentBody(BankConnection connection, Payment payment, String customerNo, String name,
            ChannelOption channel, JsonNode inquiryAnswer) {
        ObjectNode body = json.createObjectNode();
        body.put("partnerServiceId", connection.getPartnerServiceId());
        body.put("customerNo", customerNo);
        body.put("virtualAccountNo", payment.getVirtualAccountNo());
        body.put("virtualAccountName", name);
        ObjectNode paid = body.putObject("paidAmount");
        paid.put("value", (payment.getNotifiedAmount() == null ? payment.getAmount() : payment.getNotifiedAmount())
                .setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        paid.put("currency", payment.getCurrency());
        body.put("trxDateTime", SnapTimestamp.formatSeconds(payment.getPaidAt()));
        body.put("channelCode", channel.channelCode());
        body.put("sourceBankCode", SOURCE_BANK_CODE);
        if (payment.getTrxId() != null) {
            body.put("trxId", payment.getTrxId());
        }
        body.put("paymentRequestId", payment.getPaymentRequestId());
        return json.writeValueAsString(body);
    }

    @Override
    public String resendBody(String storedBody) {
        return storedBody;
    }

    /** A31: 2002500 + flag 00 acknowledges; listed 4xx or flag 01 reverses; anything else suspends. */
    @Override
    @SpecRef("bri.payment-flag-status")
    @SpecRef("bri.briva-online.payment#response.virtualAccountData.paymentFlagStatus")
    public NotificationStatus outcome(OutboundExchange answer) {
        if (answer.status() == null || answer.json() == null) {
            return NotificationStatus.SUSPENDED;
        }
        String code = answer.responseCode();
        if ("2002500".equals(code)) {
            String flag = answer.vaData("paymentFlagStatus");
            if ("00".equals(flag)) {
                return NotificationStatus.ACKNOWLEDGED;
            }
            return "01".equals(flag) ? NotificationStatus.REVERSED : NotificationStatus.SUSPENDED;
        }
        if (code != null && answer.status() >= 400 && answer.status() < 500 && answer.status() != 429
                && LISTED_PAYMENT_4XX.matcher(code).matches()) {
            return NotificationStatus.REVERSED;
        }
        return NotificationStatus.SUSPENDED;
    }
}
