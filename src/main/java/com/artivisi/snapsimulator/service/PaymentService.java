package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.BillerPaymentRequest;
import com.artivisi.snapsimulator.dto.PaymentResult;
import com.artivisi.snapsimulator.dto.VaPayRequest;
import com.artivisi.snapsimulator.entity.LedgerEntry;
import com.artivisi.snapsimulator.entity.Partner;
import com.artivisi.snapsimulator.entity.Payment;
import com.artivisi.snapsimulator.entity.VirtualAccount;
import com.artivisi.snapsimulator.enums.Channel;
import com.artivisi.snapsimulator.enums.ChecklistItem;
import com.artivisi.snapsimulator.enums.InjectionType;
import com.artivisi.snapsimulator.enums.OutboundTarget;
import com.artivisi.snapsimulator.enums.NotificationStatus;
import com.artivisi.snapsimulator.enums.VaModel;
import com.artivisi.snapsimulator.enums.VaStatus;
import com.artivisi.snapsimulator.exception.BusinessException;
import com.artivisi.snapsimulator.exception.NotFoundException;
import com.artivisi.snapsimulator.exception.OutboundException;
import com.artivisi.snapsimulator.repository.LedgerEntryRepository;
import com.artivisi.snapsimulator.repository.PaymentRepository;
import com.artivisi.snapsimulator.repository.VirtualAccountRepository;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import com.artivisi.snapsimulator.util.Randoms;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Customer payments and the bank's notification to the partner. Not one
 * transaction: the ledger credit stays when the notification fails, as at a bank.
 */
@Service
public class PaymentService {

    private static final String CURRENCY = "IDR";
    private static final String SOURCE_BANK_CODE = "002";
    private static final Pattern LISTED_PAYMENT_4XX = Pattern.compile("4(0[0-9]|1[0-9])25\\d\\d");

    private final PartnerService partners;
    private final PartnerClient client;
    private final PaymentRepository payments;
    private final VirtualAccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final ChecklistService checklist;
    private final InjectionService injections;
    private final JsonMapper json;
    private final Clock clock;

    public PaymentService(PartnerService partners, PartnerClient client, PaymentRepository payments,
            VirtualAccountRepository accounts, LedgerEntryRepository ledger, ChecklistService checklist,
            InjectionService injections, JsonMapper json, Clock clock) {
        this.injections = injections;
        this.partners = partners;
        this.client = client;
        this.payments = payments;
        this.accounts = accounts;
        this.ledger = ledger;
        this.checklist = checklist;
        this.json = json;
        this.clock = clock;
    }

    public List<Payment> list(UUID partnerId) {
        return payments.findByPartnerIdOrderByPaidAtDesc(partnerId);
    }

    /** Biller-hosted (BRIVA Online): inquiry to the partner, ledger credit, then the payment call. */
    @SpecRef("sim.biller-payment.trigger")
    @SpecRef("bri.briva-online.inquiry")
    @SpecRef("bri.briva-online.inquiry#request.inquiryRequestId")
    @SpecRef("bri.briva-online.inquiry#request.channelCode")
    @SpecRef("bri.briva-online.inquiry#request.sourceBankCode")
    public PaymentResult payBillerHosted(UUID partnerId, BillerPaymentRequest request) {
        return billerFlow(partners.get(partnerId), request.virtualAccountNo(), new BigDecimal(request.amount()),
                channel(request.channelId()), Scenario.NORMAL);
    }

    /** How the seeder distorts a biller-hosted payment; NORMAL applies the partner's injection rules instead. */
    public enum Scenario {
        NORMAL,
        DROPPED,
        AMOUNT_MISMATCH,
        DUPLICATE
    }

    /** Amount added to the notified paidAmount in the AMOUNT_MISMATCH scenario. */
    static final BigDecimal MISMATCH_DELTA = new BigDecimal("1000.00");

    /** amount null: the customer pays the totalAmount the partner answered in the inquiry. */
    PaymentResult billerFlow(Partner partner, String virtualAccountNo, BigDecimal amount, Channel channel,
            Scenario scenario) {
        UUID partnerId = partner.getId();
        String prefix = partner.getPartnerServiceId();
        if (!virtualAccountNo.startsWith(prefix) || !virtualAccountNo.substring(prefix.length()).matches("\\d{1,13}")) {
            throw new BusinessException("virtualAccountNo",
                    "VA number must be your partnerServiceId \"" + prefix + "\" followed by 1-13 digits");
        }
        String customerNo = virtualAccountNo.substring(prefix.length());
        String requestId = UUID.randomUUID().toString();
        Instant now = clock.instant();

        ObjectNode inquiry = json.createObjectNode();
        inquiry.put("partnerServiceId", prefix);
        inquiry.put("customerNo", customerNo);
        inquiry.put("virtualAccountNo", virtualAccountNo);
        ObjectNode zero = inquiry.putObject("amount");
        zero.put("value", "0.00");
        zero.put("currency", CURRENCY);
        inquiry.put("trxDateInit", SnapTimestamp.formatSeconds(now));
        inquiry.put("channelCode", channel.code());
        inquiry.put("sourceBankCode", SOURCE_BANK_CODE);
        inquiry.put("inquiryRequestId", requestId);
        PartnerClient.Exchange answer;
        try {
            boolean corrupt = scenario == Scenario.NORMAL && injections.take(partnerId, OutboundTarget.INQUIRY.name())
                    .filter(r -> r.type() == InjectionType.INVALID_SIGNATURE).isPresent();
            answer = client.post(partner, PartnerClient.INQUIRY_PATH, json.writeValueAsString(inquiry), channel.id(),
                    PartnerClient.newExternalId(), corrupt);
        } catch (OutboundException e) {
            return new PaymentResult(null, null, null, null, e.getMessage());
        }
        String inquiryCode = answer.responseCode();
        if (answer.status() == null || inquiryCode == null || !inquiryCode.startsWith("2")) {
            return new PaymentResult(null, inquiryCode, null, null,
                    "Inquiry not answered with a 2xx responseCode (" + PartnerClient.describe(answer) + "); no payment made");
        }
        checklist.stamp(partnerId, ChecklistItem.INQUIRY_ANSWERED, clock.instant());
        JsonNode name = answer.json().at("/virtualAccountData/virtualAccountName");
        BigDecimal paid = amount;
        if (paid == null) {
            JsonNode total = answer.json().at("/virtualAccountData/totalAmount/value");
            if (total.isMissingNode() || total.isNull() || !total.asString().matches("\\d{1,16}\\.\\d{2}")) {
                return new PaymentResult(null, inquiryCode, null, null,
                        "Inquiry answer has no usable virtualAccountData.totalAmount.value; no payment made");
            }
            paid = new BigDecimal(total.asString());
        }

        Payment payment = new Payment();
        payment.setPartner(partner);
        payment.setModel(VaModel.BILLER_HOSTED);
        payment.setVirtualAccountNo(virtualAccountNo);
        payment.setAmount(paid);
        if (scenario == Scenario.AMOUNT_MISMATCH) {
            payment.setNotifiedAmount(paid.add(MISMATCH_DELTA));
        }
        payment.setCurrency(CURRENCY);
        payment.setChannelId(channel.id());
        payment.setPaymentRequestId(requestId);
        payment.setNotificationStatus(NotificationStatus.PENDING);
        payment.setPaidAt(clock.instant());
        payment.setNotificationBody(paymentBody(payment, prefix, customerNo,
                name.isMissingNode() || name.isNull() ? "" : name.asString(), channel, null));
        payments.save(payment);
        credit(payment, "VA payment " + virtualAccountNo);
        return switch (scenario) {
            case NORMAL -> notifyPartner(partner, payment, inquiryCode, true);
            case DROPPED -> {
                payment.setNotificationStatus(NotificationStatus.DROPPED);
                payments.updateNotification(payment.getId(), NotificationStatus.DROPPED, null);
                yield new PaymentResult(payment.getId(), inquiryCode, null, NotificationStatus.DROPPED,
                        "Payment credited; notification not sent");
            }
            case AMOUNT_MISMATCH -> notifyPartner(partner, payment, inquiryCode, false);
            case DUPLICATE -> {
                notifyPartner(partner, payment, inquiryCode, false);
                yield notifyPartner(partner, payment, inquiryCode, false);
            }
        };
    }

    /** Bank-hosted VA paid at a channel; notified with the same payment call, carrying trxId (A25). */
    @SpecRef("sim.va.pay")
    public PaymentResult payBankHosted(UUID partnerId, VaPayRequest request) {
        Partner partner = partners.get(partnerId);
        Channel channel = channel(request.channelId());
        VirtualAccount va = accounts.findFirstByPartnerIdAndVirtualAccountNoOrderByCreatedAtDesc(partnerId,
                request.virtualAccountNo())
                .orElseThrow(() -> new BusinessException("virtualAccountNo", "No VA " + request.virtualAccountNo()));
        if (va.getStatus() == VaStatus.PAID) {
            throw new BusinessException("virtualAccountNo", "VA is already paid");
        }
        if (va.getExpiredDate() != null && !va.getExpiredDate().isAfter(clock.instant())) {
            throw new BusinessException("virtualAccountNo", "VA expired at " + SnapTimestamp.formatSeconds(va.getExpiredDate()));
        }
        va.setStatus(VaStatus.PAID);
        va.setPaidAt(clock.instant());
        accounts.save(va);

        Payment payment = new Payment();
        payment.setPartner(partner);
        payment.setVirtualAccount(va);
        payment.setModel(VaModel.BANK_HOSTED);
        payment.setVirtualAccountNo(va.getVirtualAccountNo());
        payment.setTrxId(va.getTrxId());
        payment.setAmount(va.getTotalAmount());
        payment.setCurrency(va.getCurrency());
        payment.setChannelId(channel.id());
        payment.setPaymentRequestId(UUID.randomUUID().toString());
        payment.setPaidAt(va.getPaidAt());
        payment.setNotificationStatus(partner.hasEndpoint() ? NotificationStatus.PENDING : NotificationStatus.NOT_NOTIFIED);
        payment.setNotificationBody(paymentBody(payment, partner.getPartnerServiceId(), va.getCustomerNo(),
                va.getVirtualAccountName(), channel, va.getTrxId()));
        payments.save(payment);
        credit(payment, "VA payment " + va.getVirtualAccountNo());
        if (!partner.hasEndpoint()) {
            return new PaymentResult(payment.getId(), null, null, NotificationStatus.NOT_NOTIFIED,
                    "Paid; no partner endpoint registered, so no payment notification was sent");
        }
        return notifyPartner(partner, payment, null, true);
    }

    /** Sends the stored payment call again with the same X-EXTERNAL-ID and body. */
    @SpecRef("sim.biller-payment.resend")
    public PaymentResult resend(UUID partnerId, UUID paymentId) {
        Partner partner = partners.get(partnerId);
        Payment payment = payments.findByIdAndPartnerId(paymentId, partnerId)
                .orElseThrow(() -> new NotFoundException("payment " + paymentId + " not found"));
        if (payment.getExternalId() == null) {
            throw new BusinessException(null, "This payment was never sent to the partner; nothing to resend");
        }
        PartnerClient.Exchange answer;
        try {
            answer = client.post(partner, PartnerClient.PAYMENT_PATH, payment.getNotificationBody(),
                    payment.getChannelId(), payment.getExternalId(), false);
        } catch (OutboundException e) {
            return new PaymentResult(payment.getId(), null, null, payment.getNotificationStatus(), e.getMessage());
        }
        return new PaymentResult(payment.getId(), null, answer.responseCode(), payment.getNotificationStatus(),
                "Resent with X-EXTERNAL-ID " + payment.getExternalId() + ": " + PartnerClient.describe(answer));
    }

    @SpecRef("bri.briva-online.payment")
    @SpecRef("bri.briva-online.payment#request.paidAmount")
    @SpecRef("bri.briva-online.payment#request.paymentRequestId")
    @SpecRef("bri.briva-online.payment#request.trxId")
    private String paymentBody(Payment payment, String partnerServiceId, String customerNo, String name,
            Channel channel, String trxId) {
        ObjectNode body = json.createObjectNode();
        body.put("partnerServiceId", partnerServiceId);
        body.put("customerNo", customerNo);
        body.put("virtualAccountNo", payment.getVirtualAccountNo());
        body.put("virtualAccountName", name);
        ObjectNode paid = body.putObject("paidAmount");
        paid.put("value", (payment.getNotifiedAmount() == null ? payment.getAmount() : payment.getNotifiedAmount())
                .setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString());
        paid.put("currency", payment.getCurrency());
        body.put("trxDateTime", SnapTimestamp.formatSeconds(payment.getPaidAt()));
        body.put("channelCode", channel.code());
        body.put("sourceBankCode", SOURCE_BANK_CODE);
        if (trxId != null) {
            body.put("trxId", trxId);
        }
        body.put("paymentRequestId", payment.getPaymentRequestId());
        return json.writeValueAsString(body);
    }

    /** Sends the payment call, applying the partner's PAYMENT injection rule if one is pending. */
    @SpecRef("sim.error-injection")
    private PaymentResult notifyPartner(Partner partner, Payment payment, String inquiryCode, boolean applyInjection) {
        java.util.Optional<InjectionService.Taken> rule = applyInjection
                ? injections.take(partner.getId(), OutboundTarget.PAYMENT.name())
                : java.util.Optional.empty();
        if (rule.isPresent() && rule.get().type() == InjectionType.DROP_NOTIFICATION) {
            payment.setNotificationStatus(NotificationStatus.DROPPED);
            payments.updateNotification(payment.getId(), NotificationStatus.DROPPED, null);
            return new PaymentResult(payment.getId(), inquiryCode, null, NotificationStatus.DROPPED,
                    "Payment credited; notification dropped by an injection rule");
        }
        if (rule.isPresent() && rule.get().type() == InjectionType.LATE_NOTIFICATION) {
            try {
                Thread.sleep(rule.get().delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        boolean corrupt = rule.isPresent() && rule.get().type() == InjectionType.INVALID_SIGNATURE;
        payment.setExternalId(PartnerClient.newExternalId());
        PartnerClient.Exchange answer;
        try {
            answer = client.post(partner, PartnerClient.PAYMENT_PATH, payment.getNotificationBody(),
                    payment.getChannelId(), payment.getExternalId(), corrupt);
        } catch (OutboundException e) {
            payment.setNotificationStatus(NotificationStatus.SUSPENDED);
            payments.updateNotification(payment.getId(), NotificationStatus.SUSPENDED, payment.getExternalId());
            return new PaymentResult(payment.getId(), inquiryCode, null, NotificationStatus.SUSPENDED, e.getMessage());
        }
        NotificationStatus outcome = outcome(answer);
        payment.setNotificationStatus(outcome);
        payments.updateNotification(payment.getId(), outcome, payment.getExternalId());
        if (outcome == NotificationStatus.ACKNOWLEDGED) {
            checklist.stamp(partner.getId(), ChecklistItem.PAYMENT_ACKNOWLEDGED, clock.instant());
        } else if (outcome == NotificationStatus.REVERSED) {
            reverse(payment);
        }
        return new PaymentResult(payment.getId(), inquiryCode, answer.responseCode(), outcome,
                "Payment call: " + PartnerClient.describe(answer));
    }

    /** A31: 2002500 + flag 00 acknowledges; listed 4xx or flag 01 reverses; anything else suspends. */
    @SpecRef("bri.payment-flag-status")
    @SpecRef("bri.briva-online.payment#response.virtualAccountData.paymentFlagStatus")
    static NotificationStatus outcome(PartnerClient.Exchange answer) {
        if (answer.status() == null || answer.json() == null) {
            return NotificationStatus.SUSPENDED;
        }
        String code = answer.responseCode();
        if ("2002500".equals(code)) {
            JsonNode flag = answer.json().at("/virtualAccountData/paymentFlagStatus");
            String value = flag.isMissingNode() || flag.isNull() ? null : flag.asString();
            if ("00".equals(value)) {
                return NotificationStatus.ACKNOWLEDGED;
            }
            return "01".equals(value) ? NotificationStatus.REVERSED : NotificationStatus.SUSPENDED;
        }
        if (code != null && answer.status() >= 400 && answer.status() < 500 && answer.status() != 429
                && LISTED_PAYMENT_4XX.matcher(code).matches()) {
            return NotificationStatus.REVERSED;
        }
        return NotificationStatus.SUSPENDED;
    }

    private void credit(Payment payment, String remark) {
        ledger.save(entry(payment, LedgerEntry.CREDIT, remark));
    }

    private void reverse(Payment payment) {
        ledger.save(entry(payment, LedgerEntry.DEBIT, "Reversal: partner refused payment " + payment.getPaymentRequestId()));
    }

    private LedgerEntry entry(Payment payment, String type, String remark) {
        LedgerEntry entry = new LedgerEntry();
        entry.setCreatedAt(clock.instant());
        entry.setPartner(payment.getPartner());
        entry.setPayment(payment);
        entry.setJournalId("J" + Randoms.digits(20));
        entry.setTransactionTime(clock.instant());
        entry.setEntryType(type);
        entry.setAmount(payment.getAmount());
        entry.setCurrency(payment.getCurrency());
        entry.setVirtualAccountNo(payment.getVirtualAccountNo());
        entry.setRemark(remark);
        return entry;
    }

    private static Channel channel(String id) {
        return Channel.of(id).orElseThrow(() -> new BusinessException("channelId", "Unknown channel " + id));
    }
}
