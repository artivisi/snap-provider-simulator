package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.entity.Payment;
import com.artivisi.snapsimulator.enums.NotificationStatus;
import com.artivisi.snapsimulator.exception.SnapException;
import com.artivisi.snapsimulator.repository.PaymentRepository;
import com.artivisi.snapsimulator.snap.SnapRequest;
import com.artivisi.snapsimulator.snap.SnapService;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.regex.Pattern;

/** BCA inquiry status: the payment flag the partner returned for one inquiry (A46). */
@Service
@Transactional(readOnly = true)
public class BcaStatusService {

    private static final SnapService SVC = SnapService.BCA_INQUIRY_STATUS;
    private static final Pattern PARTNER_SERVICE_ID = Pattern.compile(" *\\d{1,8}");
    private static final Pattern CUSTOMER_NO = Pattern.compile("\\d{1,18}");
    /** Flag and reason per notification outcome; outcomes not listed have no flag yet. */
    private static final Map<NotificationStatus, String[]> FLAGS = Map.of(
            NotificationStatus.ACKNOWLEDGED, new String[] {"00", "Success", "Sukses"},
            NotificationStatus.REVERSED, new String[] {"01", "Rejected by partner", "Ditolak partner"},
            NotificationStatus.SUSPENDED, new String[] {"02", "Timeout", "Waktu habis"});

    private final PaymentRepository payments;
    private final JsonMapper json;

    public BcaStatusService(PaymentRepository payments, JsonMapper json) {
        this.payments = payments;
        this.json = json;
    }

    @SpecRef("bca.va.inquiry-status")
    @SpecRef("bca.va.inquiry-status#request.inquiryRequestId")
    @SpecRef("bca.va.inquiry-status#response.virtualAccountData.paymentFlagStatus")
    @SpecRef("bca.va.number-layout")
    public ObjectNode status(BankConnection connection, JsonNode body) {
        SnapRequest request = SnapRequest.of(body, SVC);
        String partnerServiceId = request.text("partnerServiceId", true, PARTNER_SERVICE_ID);
        String customerNo = request.text("customerNo", true, CUSTOMER_NO);
        String virtualAccountNo = request.text("virtualAccountNo", true, 26);
        String inquiryRequestId = request.text("inquiryRequestId", true, 30);
        if (partnerServiceId.length() != 8) {
            throw new SnapException(SVC.invalidFieldFormat("partnerServiceId"));
        }
        if (!virtualAccountNo.equals(partnerServiceId + customerNo)) {
            throw new SnapException(SVC.invalidFieldFormat("virtualAccountNo"));
        }
        Payment payment = partnerServiceId.equals(connection.getPartnerServiceId())
                ? payments.findByConnectionIdAndVirtualAccountNoAndPaymentRequestId(connection.getId(), virtualAccountNo,
                        inquiryRequestId).orElse(null)
                : null;
        String[] flag = payment == null ? null : FLAGS.get(payment.getNotificationStatus());
        if (flag == null) {
            throw new SnapException(SVC.notFound("01", "Transaction Not Found"));
        }
        JsonNode notified = json.readTree(payment.getNotificationBody());

        ObjectNode data = json.createObjectNode();
        data.put("paymentFlagStatus", flag[0]);
        ObjectNode reason = data.putObject("paymentFlagReason");
        reason.put("english", flag[1]);
        reason.put("indonesia", flag[2]);
        data.put("partnerServiceId", partnerServiceId);
        data.put("customerNo", customerNo);
        data.put("virtualAccountNo", virtualAccountNo);
        data.put("inquiryRequestId", inquiryRequestId);
        data.put("paymentRequestId", payment.getPaymentRequestId());
        amount(data.putObject("paidAmount"), payment.getNotifiedAmount() == null ? payment.getAmount()
                : payment.getNotifiedAmount(), payment.getCurrency());
        data.put("paidBills", "");
        amount(data.putObject("totalAmount"), payment.getAmount(), payment.getCurrency());
        data.put("trxDateTime", SnapTimestamp.formatSeconds(payment.getPaidAt()));
        data.put("transactionDate", SnapTimestamp.formatSeconds(payment.getPaidAt()));
        data.put("referenceNo", payment.getReferenceNo());
        data.put("paymentType", "");
        data.put("flagAdvise", "");
        ArrayNode bills = data.putArray("billDetails");
        JsonNode sentBills = notified.get("billDetails");
        if (sentBills != null && sentBills.isArray()) {
            for (JsonNode bill : sentBills) {
                ObjectNode copy = bills.addObject();
                copy.setAll((ObjectNode) bill);
                copy.put("status", flag[0]);
                ObjectNode billReason = copy.putObject("reason");
                billReason.put("english", flag[1]);
                billReason.put("indonesia", flag[2]);
            }
        }
        data.putArray("freeTexts");
        data.putObject("additionalInfo");

        ObjectNode response = json.createObjectNode();
        response.put("responseCode", "2002600");
        response.put("responseMessage", "Success");
        response.set("virtualAccountData", data);
        return response;
    }

    private static void amount(ObjectNode node, BigDecimal value, String currency) {
        node.put("value", value.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        node.put("currency", currency);
    }
}
