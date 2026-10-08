package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.entity.Partner;
import com.artivisi.snapsimulator.entity.Payment;
import com.artivisi.snapsimulator.entity.VirtualAccount;
import com.artivisi.snapsimulator.enums.ChecklistItem;
import com.artivisi.snapsimulator.enums.VaStatus;
import com.artivisi.snapsimulator.exception.SnapException;
import com.artivisi.snapsimulator.repository.PaymentRepository;
import com.artivisi.snapsimulator.repository.VirtualAccountRepository;
import com.artivisi.snapsimulator.snap.SnapRequest;
import com.artivisi.snapsimulator.snap.SnapService;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bank-hosted VA operations (ASPI VA standard under BRI's /snap/v1.0 prefix, A34). */
@Service
@Transactional(readOnly = true)
public class VirtualAccountService {

    private static final Pattern PARTNER_SERVICE_ID = Pattern.compile(" *\\d{1,8}");
    private static final Pattern CUSTOMER_NO = Pattern.compile("\\d{1,13}");
    /** Fields stored as sent and echoed back in virtualAccountData. */
    private static final List<String> EXTRA_FIELDS = List.of("billDetails", "freeTexts", "virtualAccountTrxType",
            "feeAmount", "additionalInfo");

    private final VirtualAccountRepository accounts;
    private final PaymentRepository payments;
    private final ChecklistService checklist;
    private final JsonMapper json;

    public VirtualAccountService(VirtualAccountRepository accounts, PaymentRepository payments,
            ChecklistService checklist, JsonMapper json) {
        this.accounts = accounts;
        this.payments = payments;
        this.checklist = checklist;
        this.json = json;
    }

    public List<VirtualAccount> list(UUID partnerId) {
        return accounts.findByPartnerIdOrderByCreatedAtDesc(partnerId);
    }

    /** partnerServiceId, customerNo and virtualAccountNo, checked against each other and the partner (A17, A18). */
    @SpecRef("bri.va.number-layout")
    record VaNumber(String partnerServiceId, String customerNo, String virtualAccountNo) {
    }

    @SpecRef("bri.va.number-layout")
    VaNumber vaNumber(SnapRequest request, Partner partner) {
        String partnerServiceId = request.text("partnerServiceId", true, PARTNER_SERVICE_ID);
        String customerNo = request.text("customerNo", true, CUSTOMER_NO);
        String virtualAccountNo = request.text("virtualAccountNo", true, 28);
        if (partnerServiceId.length() != 8) {
            throw new SnapException(request.service().invalidFieldFormat("partnerServiceId"));
        }
        if (!virtualAccountNo.equals(partnerServiceId + customerNo)) {
            throw new SnapException(request.service().invalidFieldFormat("virtualAccountNo"));
        }
        if (!partnerServiceId.equals(partner.getPartnerServiceId())) {
            throw new SnapException(request.service().notFound("16", "Partner Not Found"));
        }
        return new VaNumber(partnerServiceId, customerNo, virtualAccountNo);
    }

    @Transactional
    @SpecRef("aspi.va.create-va")
    @SpecRef("aspi.va.create-va#request.virtualAccountName")
    @SpecRef("aspi.va.create-va#request.trxId")
    @SpecRef("aspi.va.create-va#request.totalAmount")
    @SpecRef("aspi.va.create-va#request.expiredDate")
    @SpecRef("aspi.va.trx-type")
    public ObjectNode create(Partner partner, JsonNode body, Instant now) {
        SnapRequest request = SnapRequest.of(body, SnapService.CREATE_VA);
        VaNumber number = vaNumber(request, partner);
        String name = request.text("virtualAccountName", true, 255);
        String trxId = request.text("trxId", true, 64);
        SnapRequest.Amount total = request.amount("totalAmount", true);
        String email = request.text("virtualAccountEmail", false, 255);
        String phone = request.text("virtualAccountPhone", false, 30);
        String trxType = request.text("virtualAccountTrxType", false, 1);
        if (trxType != null && !"C".equals(trxType)) {
            throw new SnapException(request.service().invalidFieldFormat("virtualAccountTrxType"));
        }
        request.amount("feeAmount", false);
        Instant expiredDate = request.dateTime("expiredDate");
        if (expiredDate != null && !expiredDate.isAfter(now)) {
            throw new SnapException(request.service().invalidFieldFormat("expiredDate"));
        }
        if (accounts.existsByPartnerIdAndTrxId(partner.getId(), trxId)
                || accounts.existsByPartnerIdAndVirtualAccountNoAndStatus(partner.getId(), number.virtualAccountNo(),
                        VaStatus.ACTIVE)) {
            throw new SnapException(request.service().conflict("01", "Duplicate partnerReferenceNo"));
        }

        VirtualAccount va = new VirtualAccount();
        va.setPartner(partner);
        va.setCustomerNo(number.customerNo());
        va.setVirtualAccountNo(number.virtualAccountNo());
        va.setVirtualAccountName(name);
        va.setVirtualAccountEmail(email);
        va.setVirtualAccountPhone(phone);
        va.setTrxId(trxId);
        va.setTotalAmount(total.decimal());
        va.setCurrency(total.currency());
        va.setExpiredDate(expiredDate);
        va.setStatus(VaStatus.ACTIVE);
        va.setExtraJson(extra(body));
        accounts.save(va);
        checklist.stamp(partner.getId(), ChecklistItem.VA_CREATED, now);
        return success(request.service(), vaData(va, partner, false));
    }

    @Transactional
    @SpecRef("aspi.va.update-va")
    @SpecRef("aspi.va.update-va#request.trxId")
    public ObjectNode update(Partner partner, JsonNode body, Instant now) {
        SnapRequest request = SnapRequest.of(body, SnapService.UPDATE_VA);
        VaNumber number = vaNumber(request, partner);
        String trxId = request.text("trxId", true, 64);
        String name = request.text("virtualAccountName", true, 255);
        SnapRequest.Amount total = request.amount("totalAmount", false);
        String email = request.text("virtualAccountEmail", false, 255);
        String phone = request.text("virtualAccountPhone", false, 30);
        String trxType = request.text("virtualAccountTrxType", false, 1);
        if (trxType != null && !"C".equals(trxType)) {
            throw new SnapException(request.service().invalidFieldFormat("virtualAccountTrxType"));
        }
        request.amount("feeAmount", false);
        Instant expiredDate = request.dateTime("expiredDate");

        VirtualAccount va = find(request, partner, number, trxId);
        requireUnpaid(request, va);
        requireNotExpired(request, va, now);
        va.setVirtualAccountName(name);
        va.setVirtualAccountEmail(email);
        va.setVirtualAccountPhone(phone);
        if (total != null) {
            va.setTotalAmount(total.decimal());
            va.setCurrency(total.currency());
        }
        va.setExpiredDate(expiredDate);
        va.setExtraJson(extra(body));
        accounts.flush();
        return success(request.service(), vaData(va, partner, true));
    }

    @SpecRef("aspi.va.inquiry-va")
    @SpecRef("aspi.va.inquiry-va#request.trxId")
    public ObjectNode inquiry(Partner partner, JsonNode body, Instant now) {
        SnapRequest request = SnapRequest.of(body, SnapService.INQUIRY_VA);
        VaNumber number = vaNumber(request, partner);
        String trxId = request.text("trxId", true, 64);
        VirtualAccount va = find(request, partner, number, trxId);
        requireNotExpired(request, va, now);
        return success(request.service(), vaData(va, partner, true));
    }

    @Transactional
    @SpecRef("aspi.va.delete-va")
    public ObjectNode delete(Partner partner, JsonNode body) {
        SnapRequest request = SnapRequest.of(body, SnapService.DELETE_VA);
        VaNumber number = vaNumber(request, partner);
        String trxId = request.text("trxId", false, 64);
        VirtualAccount va = find(request, partner, number, trxId);
        requireUnpaid(request, va);
        accounts.delete(va);
        ObjectNode data = json.createObjectNode();
        data.put("partnerServiceId", number.partnerServiceId());
        data.put("customerNo", number.customerNo());
        data.put("virtualAccountNo", number.virtualAccountNo());
        data.put("trxId", va.getTrxId());
        JsonNode additionalInfo = body.get("additionalInfo");
        if (additionalInfo != null && !additionalInfo.isNull()) {
            data.set("additionalInfo", additionalInfo);
        }
        return success(request.service(), data);
    }

    /** Matches by virtualAccountNo, then paymentRequestId when given; paid answers flag 00 (A24). */
    @SpecRef("aspi.va.inquiry-status")
    @SpecRef("bri.payment-flag-status")
    public ObjectNode status(Partner partner, JsonNode body) {
        SnapRequest request = SnapRequest.of(body, SnapService.INQUIRY_STATUS);
        VaNumber number = vaNumber(request, partner);
        String inquiryRequestId = request.text("inquiryRequestId", false, 128);
        String paymentRequestId = request.text("paymentRequestId", false, 128);
        boolean vaKnown = accounts.findFirstByPartnerIdAndVirtualAccountNoOrderByCreatedAtDesc(partner.getId(),
                number.virtualAccountNo()).isPresent();
        Payment payment = (paymentRequestId == null
                ? payments.findFirstByPartnerIdAndVirtualAccountNoOrderByPaidAtDesc(partner.getId(), number.virtualAccountNo())
                : payments.findByPartnerIdAndVirtualAccountNoAndPaymentRequestId(partner.getId(),
                        number.virtualAccountNo(), paymentRequestId))
                .orElse(null);
        if (payment == null) {
            throw new SnapException(vaKnown
                    ? request.service().notFound("01", "Transaction Not Found")
                    : request.service().notFound("12", "Invalid Bill/Virtual Account Not Found"));
        }
        ObjectNode data = json.createObjectNode();
        ObjectNode reason = data.putObject("paymentFlagReason");
        reason.put("english", "Success");
        reason.put("indonesia", "Sukses");
        data.put("partnerServiceId", number.partnerServiceId());
        data.put("customerNo", number.customerNo());
        data.put("virtualAccountNo", number.virtualAccountNo());
        data.put("inquiryRequestId", inquiryRequestId == null ? payment.getPaymentRequestId() : inquiryRequestId);
        data.put("paymentRequestId", payment.getPaymentRequestId());
        amount(data.putObject("paidAmount"), payment.getAmount(), payment.getCurrency());
        amount(data.putObject("totalAmount"), payment.getAmount(), payment.getCurrency());
        data.put("trxDateTime", SnapTimestamp.formatSeconds(payment.getPaidAt()));
        data.put("transactionDate", SnapTimestamp.formatSeconds(payment.getPaidAt()));
        data.put("paymentFlagStatus", "00");
        return success(request.service(), data);
    }

    private VirtualAccount find(SnapRequest request, Partner partner, VaNumber number, String trxId) {
        VirtualAccount va = accounts.findFirstByPartnerIdAndVirtualAccountNoOrderByCreatedAtDesc(partner.getId(),
                number.virtualAccountNo())
                .orElseThrow(() -> new SnapException(request.service().notFound("12", "Invalid Bill/Virtual Account Not Found")));
        if (trxId != null && !trxId.equals(va.getTrxId())) {
            throw new SnapException(request.service().notFound("12", "Invalid Bill/Virtual Account trxId mismatch"));
        }
        return va;
    }

    private static void requireUnpaid(SnapRequest request, VirtualAccount va) {
        if (va.getStatus() == VaStatus.PAID) {
            throw new SnapException(request.service().notFound("14", "Paid Bill"));
        }
    }

    private static void requireNotExpired(SnapRequest request, VirtualAccount va, Instant now) {
        if (va.getExpiredDate() != null && !va.getExpiredDate().isAfter(now)) {
            throw new SnapException(request.service().notFound("19", "Invalid Bill/Virtual Account"));
        }
    }

    private String extra(JsonNode body) {
        ObjectNode extra = json.createObjectNode();
        for (String field : EXTRA_FIELDS) {
            JsonNode value = body.get(field);
            if (value != null && !value.isNull()) {
                extra.set(field, value);
            }
        }
        return json.writeValueAsString(extra);
    }

    ObjectNode vaData(VirtualAccount va, Partner partner, boolean withDates) {
        ObjectNode data = json.createObjectNode();
        data.put("partnerServiceId", partner.getPartnerServiceId());
        data.put("customerNo", va.getCustomerNo());
        data.put("virtualAccountNo", va.getVirtualAccountNo());
        data.put("virtualAccountName", va.getVirtualAccountName());
        if (va.getVirtualAccountEmail() != null) {
            data.put("virtualAccountEmail", va.getVirtualAccountEmail());
        }
        if (va.getVirtualAccountPhone() != null) {
            data.put("virtualAccountPhone", va.getVirtualAccountPhone());
        }
        data.put("trxId", va.getTrxId());
        amount(data.putObject("totalAmount"), va.getTotalAmount(), va.getCurrency());
        JsonNode extra = json.readTree(va.getExtraJson());
        extra.properties().forEach(e -> data.set(e.getKey(), e.getValue()));
        if (va.getExpiredDate() != null) {
            data.put("expiredDate", SnapTimestamp.formatSeconds(va.getExpiredDate()));
        }
        if (withDates) {
            data.put("lastUpdateDate", SnapTimestamp.formatSeconds(va.getUpdatedAt()));
            if (va.getPaidAt() != null) {
                data.put("paymentDate", SnapTimestamp.formatSeconds(va.getPaidAt()));
            }
        }
        return data;
    }

    private static void amount(ObjectNode node, java.math.BigDecimal value, String currency) {
        node.put("value", value.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString());
        node.put("currency", currency);
    }

    private ObjectNode success(SnapService svc, ObjectNode data) {
        ObjectNode response = json.createObjectNode();
        response.put("responseCode", svc.success().code());
        response.put("responseMessage", svc.success().message());
        response.set("virtualAccountData", data);
        return response;
    }
}
