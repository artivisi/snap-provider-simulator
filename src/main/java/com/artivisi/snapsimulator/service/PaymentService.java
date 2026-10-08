package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.bank.BankProfile;
import com.artivisi.snapsimulator.bank.BankProfiles;
import com.artivisi.snapsimulator.bank.ChannelOption;
import com.artivisi.snapsimulator.dto.BillerPaymentRequest;
import com.artivisi.snapsimulator.dto.OutboundExchange;
import com.artivisi.snapsimulator.dto.PaymentResult;
import com.artivisi.snapsimulator.dto.VaPayRequest;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.entity.LedgerEntry;
import com.artivisi.snapsimulator.entity.Payment;
import com.artivisi.snapsimulator.entity.VirtualAccount;
import com.artivisi.snapsimulator.enums.ChecklistItem;
import com.artivisi.snapsimulator.enums.InjectionType;
import com.artivisi.snapsimulator.enums.NotificationStatus;
import com.artivisi.snapsimulator.enums.OutboundTarget;
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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Customer payments and the bank's notification to the partner, shaped by the
 * connection's bank profile. Not one transaction: the ledger credit stays when
 * the notification fails, as at a bank.
 */
@Service
public class PaymentService {

    private static final String CURRENCY = "IDR";
    /** Amount added to the notified paidAmount in the AMOUNT_MISMATCH scenario. */
    static final BigDecimal MISMATCH_DELTA = new BigDecimal("1000.00");

    /** How the seeder distorts a biller-hosted payment; NORMAL applies the injection rules instead. */
    public enum Scenario {
        NORMAL,
        DROPPED,
        AMOUNT_MISMATCH,
        DUPLICATE
    }

    private final PartnerClient client;
    private final PaymentRepository payments;
    private final VirtualAccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final ChecklistService checklist;
    private final InjectionService injections;
    private final BankProfiles profiles;
    private final JsonMapper json;
    private final Clock clock;

    public PaymentService(PartnerClient client, PaymentRepository payments,
            VirtualAccountRepository accounts, LedgerEntryRepository ledger, ChecklistService checklist,
            InjectionService injections, BankProfiles profiles, JsonMapper json, Clock clock) {
        this.client = client;
        this.payments = payments;
        this.accounts = accounts;
        this.ledger = ledger;
        this.checklist = checklist;
        this.injections = injections;
        this.profiles = profiles;
        this.json = json;
        this.clock = clock;
    }

    public List<Payment> list(UUID connectionId) {
        return payments.findByConnectionIdOrderByPaidAtDesc(connectionId);
    }

    /** Biller-hosted: inquiry to the partner, ledger credit, then the payment call. */
    @SpecRef("sim.biller-payment.trigger")
    public PaymentResult payBillerHosted(BankConnection connection, BillerPaymentRequest request) {
        BankProfile profile = profiles.of(connection.getBank());
        return billerFlow(connection, request.virtualAccountNo(), new BigDecimal(request.amount()),
                profile.channel(request.channelId()), Scenario.NORMAL);
    }

    /** amount null: the customer pays the totalAmount the partner answered in the inquiry. */
    PaymentResult billerFlow(BankConnection connection, String virtualAccountNo, BigDecimal amount, ChannelOption channel,
            Scenario scenario) {
        BankProfile profile = profiles.of(connection.getBank());
        String prefix = connection.getPartnerServiceId();
        String digits = "\\d{1," + profile.maxCustomerNoDigits() + "}";
        if (!virtualAccountNo.startsWith(prefix) || !virtualAccountNo.substring(prefix.length()).matches(digits)) {
            throw new BusinessException("virtualAccountNo", "VA number must be your partnerServiceId \"" + prefix
                    + "\" followed by 1-" + profile.maxCustomerNoDigits() + " digits");
        }
        String customerNo = virtualAccountNo.substring(prefix.length());
        String requestId = profile.newRequestId(clock.instant());
        String inquiry = json.writeValueAsString(
                profile.inquiryBody(connection, customerNo, virtualAccountNo, channel, requestId, clock.instant()));
        OutboundExchange answer;
        try {
            boolean corrupt = scenario == Scenario.NORMAL
                    && injections.take(connection.getId(), OutboundTarget.INQUIRY.name())
                            .filter(r -> r.type() == InjectionType.INVALID_SIGNATURE).isPresent();
            answer = client.post(connection, PartnerClient.INQUIRY_PATH, inquiry, channel, PartnerClient.newExternalId(),
                    corrupt);
        } catch (OutboundException e) {
            return new PaymentResult(null, null, null, null, e.getMessage());
        }
        String inquiryCode = answer.responseCode();
        if (!profile.inquiryAnswered(answer)) {
            return new PaymentResult(null, inquiryCode, null, null,
                    "Inquiry not answered as successful (" + answer.describe() + "); no payment made");
        }
        checklist.stamp(connection.getId(), ChecklistItem.INQUIRY_ANSWERED, clock.instant());
        BigDecimal paid = amount;
        if (paid == null) {
            String total = answer.vaData("totalAmount/value");
            if (total == null || !total.matches("\\d{1,16}\\.\\d{2}")) {
                return new PaymentResult(null, inquiryCode, null, null,
                        "Inquiry answer has no usable virtualAccountData.totalAmount.value; no payment made");
            }
            paid = new BigDecimal(total);
        }
        String name = answer.vaData("virtualAccountName");

        Payment payment = new Payment();
        payment.setConnection(connection);
        payment.setModel(VaModel.BILLER_HOSTED);
        payment.setVirtualAccountNo(virtualAccountNo);
        payment.setAmount(paid);
        if (scenario == Scenario.AMOUNT_MISMATCH) {
            payment.setNotifiedAmount(paid.add(MISMATCH_DELTA));
        }
        payment.setCurrency(CURRENCY);
        payment.setChannelId(channel.code());
        payment.setPaymentRequestId(requestId);
        payment.setReferenceNo(profile.newReferenceNo());
        payment.setNotificationStatus(NotificationStatus.PENDING);
        payment.setPaidAt(clock.instant());
        payment.setNotificationBody(profile.paymentBody(connection, payment, customerNo, name == null ? "" : name,
                channel, answer.json()));
        payments.save(payment);
        credit(payment, "VA payment " + virtualAccountNo);
        return switch (scenario) {
            case NORMAL -> notifyPartner(connection, payment, channel, inquiryCode, true);
            case DROPPED -> {
                payment.setNotificationStatus(NotificationStatus.DROPPED);
                payments.updateNotification(payment.getId(), NotificationStatus.DROPPED, null);
                yield new PaymentResult(payment.getId(), inquiryCode, null, NotificationStatus.DROPPED,
                        "Payment credited; notification not sent");
            }
            case AMOUNT_MISMATCH -> notifyPartner(connection, payment, channel, inquiryCode, false);
            case DUPLICATE -> {
                notifyPartner(connection, payment, channel, inquiryCode, false);
                yield notifyPartner(connection, payment, channel, inquiryCode, false);
            }
        };
    }

    /** Bank-hosted VA paid at a channel; notified with the same payment call, carrying trxId (A25). */
    @SpecRef("sim.va.pay")
    public PaymentResult payBankHosted(BankConnection connection, VaPayRequest request) {
        BankProfile profile = profiles.of(connection.getBank());
        if (!profile.bankHostedVa()) {
            throw new BusinessException(null, connection.getBank() + " has no bank-hosted VAs");
        }
        ChannelOption channel = profile.channel(request.channelId());
        VirtualAccount va = accounts.findFirstByConnectionIdAndVirtualAccountNoOrderByCreatedAtDesc(connection.getId(),
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
        payment.setConnection(connection);
        payment.setVirtualAccount(va);
        payment.setModel(VaModel.BANK_HOSTED);
        payment.setVirtualAccountNo(va.getVirtualAccountNo());
        payment.setTrxId(va.getTrxId());
        payment.setAmount(va.getTotalAmount());
        payment.setCurrency(va.getCurrency());
        payment.setChannelId(channel.code());
        payment.setPaymentRequestId(profile.newRequestId(clock.instant()));
        payment.setReferenceNo(profile.newReferenceNo());
        payment.setPaidAt(va.getPaidAt());
        payment.setNotificationStatus(connection.hasEndpoint() ? NotificationStatus.PENDING : NotificationStatus.NOT_NOTIFIED);
        payment.setNotificationBody(profile.paymentBody(connection, payment, va.getCustomerNo(),
                va.getVirtualAccountName(), channel, null));
        payments.save(payment);
        credit(payment, "VA payment " + va.getVirtualAccountNo());
        if (!connection.hasEndpoint()) {
            return new PaymentResult(payment.getId(), null, null, NotificationStatus.NOT_NOTIFIED,
                    "Paid; no partner endpoint registered, so no payment notification was sent");
        }
        return notifyPartner(connection, payment, channel, null, true);
    }

    /** Sends the stored payment call again with the same X-EXTERNAL-ID (BCA: flagAdvise Y, A45). */
    @SpecRef("sim.biller-payment.resend")
    public PaymentResult resend(BankConnection connection, UUID paymentId) {
        Payment payment = payments.findByIdAndConnectionId(paymentId, connection.getId())
                .orElseThrow(() -> new NotFoundException("payment " + paymentId + " not found"));
        if (payment.getExternalId() == null) {
            throw new BusinessException(null, "This payment was never sent to the partner; nothing to resend");
        }
        BankProfile profile = profiles.of(connection.getBank());
        OutboundExchange answer;
        try {
            answer = client.post(connection, PartnerClient.PAYMENT_PATH, profile.resendBody(payment.getNotificationBody()),
                    profile.channel(payment.getChannelId()), payment.getExternalId(), false);
        } catch (OutboundException e) {
            return new PaymentResult(payment.getId(), null, null, payment.getNotificationStatus(), e.getMessage());
        }
        return new PaymentResult(payment.getId(), null, answer.responseCode(), payment.getNotificationStatus(),
                "Resent with X-EXTERNAL-ID " + payment.getExternalId() + ": " + answer.describe());
    }

    /** Sends the payment call, applying the connection's PAYMENT injection rule if one is pending. */
    @SpecRef("sim.error-injection")
    private PaymentResult notifyPartner(BankConnection connection, Payment payment, ChannelOption channel,
            String inquiryCode, boolean applyInjection) {
        Optional<InjectionService.Taken> rule = applyInjection
                ? injections.take(connection.getId(), OutboundTarget.PAYMENT.name())
                : Optional.empty();
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
        OutboundExchange answer;
        try {
            answer = client.post(connection, PartnerClient.PAYMENT_PATH, payment.getNotificationBody(), channel,
                    payment.getExternalId(), corrupt);
        } catch (OutboundException e) {
            payment.setNotificationStatus(NotificationStatus.SUSPENDED);
            payments.updateNotification(payment.getId(), NotificationStatus.SUSPENDED, payment.getExternalId());
            return new PaymentResult(payment.getId(), inquiryCode, null, NotificationStatus.SUSPENDED, e.getMessage());
        }
        NotificationStatus outcome = profiles.of(connection.getBank()).outcome(answer);
        payment.setNotificationStatus(outcome);
        payments.updateNotification(payment.getId(), outcome, payment.getExternalId());
        if (outcome == NotificationStatus.ACKNOWLEDGED) {
            checklist.stamp(connection.getId(), ChecklistItem.PAYMENT_ACKNOWLEDGED, clock.instant());
        } else if (outcome == NotificationStatus.REVERSED) {
            ledger.save(entry(payment, LedgerEntry.DEBIT, "Reversal: partner refused payment " + payment.getPaymentRequestId()));
        }
        return new PaymentResult(payment.getId(), inquiryCode, answer.responseCode(), outcome,
                "Payment call: " + answer.describe());
    }

    private void credit(Payment payment, String remark) {
        ledger.save(entry(payment, LedgerEntry.CREDIT, remark));
    }

    private LedgerEntry entry(Payment payment, String type, String remark) {
        LedgerEntry entry = new LedgerEntry();
        entry.setCreatedAt(clock.instant());
        entry.setConnection(payment.getConnection());
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
}
