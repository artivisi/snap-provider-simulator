package com.artivisi.snapsimulator.dto;

import com.artivisi.snapsimulator.entity.Payment;
import com.artivisi.snapsimulator.enums.NotificationStatus;
import com.artivisi.snapsimulator.enums.VaModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentView(UUID id, VaModel model, String virtualAccountNo, String trxId, BigDecimal amount,
        String currency, String channelId, String paymentRequestId, String externalId,
        NotificationStatus notificationStatus, Instant paidAt) {

    public static PaymentView of(Payment p) {
        return new PaymentView(p.getId(), p.getModel(), p.getVirtualAccountNo(), p.getTrxId(), p.getAmount(),
                p.getCurrency(), p.getChannelId(), p.getPaymentRequestId(), p.getExternalId(),
                p.getNotificationStatus(), p.getPaidAt());
    }
}
