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
import com.artivisi.snapsimulator.util.Randoms;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** BCA: public "Virtual Account for Biller" docs; biller-hosted only, no create-va. */
@Component
public class BcaProfile implements BankProfile {

    /** WSID BCA Virtual Account, sent as CHANNEL-ID on every call (A40). */
    static final String CHANNEL_ID = "95231";
    private static final String SOURCE_BANK_CODE = "014";
    private static final Set<String> FLAG_CODES = Set.of("2002500", "2022500", "4042518");
    private static final Set<String> INQUIRY_CODES = Set.of("2002400", "2022400");
    private static final DateTimeFormatter REQUEST_ID_PREFIX = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String[] BILL_FIELDS = {"billCode", "billNo", "billName", "billShortName", "billDescription",
        "billSubCompany", "billAmount", "additionalInfo"};

    @SpecRef("bca.channel-code")
    private static final List<ChannelOption> CHANNELS = List.of(
            channel("6010", "Teller"), channel("6011", "ATM"), channel("6012", "EDC"), channel("6013", "Autodebet"),
            channel("6014", "Internet Banking"), channel("6015", "Oneklik"), channel("6016", "myBCA"),
            channel("6017", "Mobile Banking"), channel("6018", "Other Bank"), channel("6019", "Cardless"),
            channel("6020", "Shared Biller"), channel("6000", "Others"));

    private final JsonMapper json;

    public BcaProfile(JsonMapper json) {
        this.json = json;
    }

    private static ChannelOption channel(String code, String label) {
        return new ChannelOption(code, label, CHANNEL_ID, Integer.parseInt(code));
    }

    @Override
    public Bank bank() {
        return Bank.BCA;
    }

    @Override
    public SnapService tokenService() {
        return SnapService.BCA_ACCESS_TOKEN;
    }

    /** A38. */
    @Override
    @SpecRef("bca.oauth.token-b2b")
    @SpecRef("bca.oauth.token-b2b#response.responseCode")
    @SpecRef("bca.oauth.token-b2b#response.tokenType")
    public ObjectNode tokenResponse(String accessToken, int ttlSeconds) {
        ObjectNode body = json.createObjectNode();
        body.put("responseCode", "2007300");
        body.put("responseMessage", "Successful");
        body.put("accessToken", accessToken);
        body.put("tokenType", "bearer");
        body.put("expiresIn", String.valueOf(ttlSeconds));
        return body;
    }

    /** Must be the connection's company code (A36). */
    @Override
    @SpecRef("bca.headers.service")
    @SpecRef("bca.headers.service#request.X-PARTNER-ID")
    public void checkPartnerIdHeader(String value, BankConnection connection, SnapService svc) {
        if (!value.equals(connection.companyCode())) {
            throw new SnapException(svc.invalidFieldFormat("X-PARTNER-ID"));
        }
    }

    /** Path plus the query string with parameters sorted by name, then value (A37). */
    @Override
    @SpecRef("bca.sig.relative-url")
    public String signedPath(String path, String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return path;
        }
        TreeMap<String, List<String>> sorted = new TreeMap<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            sorted.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(pair);
        }
        StringBuilder sb = new StringBuilder(path).append('?');
        sorted.values().forEach(pairs -> pairs.stream().sorted().forEach(p -> sb.append(p).append('&')));
        return sb.substring(0, sb.length() - 1);
    }

    @Override
    @SpecRef("bca.va.number-layout")
    public int maxCustomerNoDigits() {
        return 18;
    }

    /** No create-va, so no "VA created" step. */
    @Override
    public List<ChecklistItem> checklist() {
        return Arrays.stream(ChecklistItem.values()).filter(i -> i != ChecklistItem.VA_CREATED).toList();
    }

    @Override
    public boolean bankHostedVa() {
        return false;
    }

    /** 25 characters, no milliseconds (A35). */
    @Override
    public String timestamp(Instant instant) {
        return SnapTimestamp.formatSeconds(instant);
    }

    @Override
    public List<ChannelOption> channels() {
        return CHANNELS;
    }

    /** The company code (A40). */
    @Override
    public String outboundPartnerId(BankConnection connection) {
        return connection.companyCode();
    }

    /** yyyyMMddHHmmss + 16 digits = 30 characters (A41). */
    @Override
    @SpecRef("bca.va.inquiry#request.inquiryRequestId")
    public String newRequestId(Instant now) {
        return REQUEST_ID_PREFIX.format(now.atZone(SnapTimestamp.JAKARTA)) + Randoms.digits(16);
    }

    @Override
    public String newReferenceNo() {
        return Randoms.digits(11);
    }

    /** A47. */
    @Override
    @SpecRef("bca.va.inquiry")
    @SpecRef("bca.va.inquiry#request.trxDateInit")
    @SpecRef("bca.va.inquiry#request.channelCode")
    public ObjectNode inquiryBody(BankConnection connection, String customerNo, String virtualAccountNo,
            ChannelOption channel, String requestId, Instant now) {
        ObjectNode inquiry = json.createObjectNode();
        inquiry.put("partnerServiceId", connection.getPartnerServiceId());
        inquiry.put("customerNo", customerNo);
        inquiry.put("virtualAccountNo", virtualAccountNo);
        inquiry.put("trxDateInit", SnapTimestamp.formatSeconds(now));
        inquiry.put("channelCode", channel.channelCode());
        inquiry.put("language", "");
        inquiry.putNull("amount");
        inquiry.put("hashedSourceAccountNo", "");
        inquiry.put("sourceBankCode", SOURCE_BANK_CODE);
        inquiry.putObject("additionalInfo");
        inquiry.put("passApp", "");
        inquiry.put("inquiryRequestId", requestId);
        return inquiry;
    }

    /** 2002400 or 2022400 with inquiryStatus 00 (A42). */
    @Override
    @SpecRef("bca.inquiry-status")
    @SpecRef("bca.va.inquiry#response.virtualAccountData.inquiryStatus")
    public boolean inquiryAnswered(OutboundExchange answer) {
        return answer.status() != null && INQUIRY_CODES.contains(answer.responseCode())
                && "00".equals(answer.vaData("inquiryStatus"));
    }

    /** All bills from the inquiry are paid; each gets its own billReferenceNo (A44). */
    @Override
    @SpecRef("bca.va.payment")
    @SpecRef("bca.va.payment#request.paymentRequestId")
    @SpecRef("bca.va.payment#request.referenceNo")
    @SpecRef("bca.va.payment#request.flagAdvise")
    @SpecRef("bca.va.payment#request.billDetails[].billReferenceNo")
    public String paymentBody(BankConnection connection, Payment payment, String customerNo, String name,
            ChannelOption channel, JsonNode inquiryAnswer) {
        ObjectNode body = json.createObjectNode();
        body.put("partnerServiceId", connection.getPartnerServiceId());
        body.put("customerNo", customerNo);
        body.put("virtualAccountNo", payment.getVirtualAccountNo());
        body.put("virtualAccountName", name);
        body.put("virtualAccountEmail", "");
        body.put("virtualAccountPhone", "");
        body.put("trxId", "");
        body.put("paymentRequestId", payment.getPaymentRequestId());
        body.put("channelCode", channel.channelCode());
        body.put("hashedSourceAccountNo", "");
        body.put("sourceBankCode", SOURCE_BANK_CODE);
        amount(body.putObject("paidAmount"), payment.getNotifiedAmount() == null ? payment.getAmount()
                : payment.getNotifiedAmount(), payment.getCurrency());
        body.putNull("cumulativePaymentAmount");
        body.put("paidBills", "");
        amount(body.putObject("totalAmount"), payment.getAmount(), payment.getCurrency());
        body.put("trxDateTime", SnapTimestamp.formatSeconds(payment.getPaidAt()));
        body.put("referenceNo", payment.getReferenceNo());
        body.put("journalNum", "");
        body.put("paymentType", "");
        body.put("flagAdvise", "N");
        JsonNode data = inquiryAnswer == null ? null : inquiryAnswer.get("virtualAccountData");
        JsonNode subCompany = data == null ? null : data.get("subCompany");
        body.put("subCompany", subCompany == null || subCompany.isNull() ? "" : subCompany.asString());
        ArrayNode bills = body.putArray("billDetails");
        JsonNode inquiryBills = data == null ? null : data.get("billDetails");
        if (inquiryBills != null && inquiryBills.isArray()) {
            boolean first = true;
            for (JsonNode bill : inquiryBills) {
                ObjectNode copy = bills.addObject();
                for (String field : BILL_FIELDS) {
                    if (bill.has(field)) {
                        copy.set(field, bill.get(field));
                    }
                }
                copy.put("billReferenceNo", first ? payment.getReferenceNo() : Randoms.digits(11));
                first = false;
            }
        }
        body.putArray("freeTexts");
        body.putObject("additionalInfo");
        return json.writeValueAsString(body);
    }

    /** Same X-EXTERNAL-ID and paymentRequestId, flagAdvise Y (A45). */
    @Override
    @SpecRef("bca.va.payment#request.flagAdvise")
    public String resendBody(String storedBody) {
        ObjectNode body = (ObjectNode) json.readTree(storedBody);
        body.put("flagAdvise", "Y");
        return json.writeValueAsString(body);
    }

    /** A43. */
    @Override
    @SpecRef("bca.payment-flag-status")
    @SpecRef("bca.va.payment#response.virtualAccountData.paymentFlagStatus")
    public NotificationStatus outcome(OutboundExchange answer) {
        if (answer.status() == null) {
            return NotificationStatus.SUSPENDED;
        }
        if (!FLAG_CODES.contains(answer.responseCode())) {
            return NotificationStatus.REVERSED;
        }
        String flag = answer.vaData("paymentFlagStatus");
        if ("00".equals(flag)) {
            return NotificationStatus.ACKNOWLEDGED;
        }
        return "01".equals(flag) ? NotificationStatus.REVERSED : NotificationStatus.SUSPENDED;
    }

    private static void amount(ObjectNode node, java.math.BigDecimal value, String currency) {
        node.put("value", value.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        node.put("currency", currency);
    }
}
