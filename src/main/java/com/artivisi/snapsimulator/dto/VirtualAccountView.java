package com.artivisi.snapsimulator.dto;

import com.artivisi.snapsimulator.entity.VirtualAccount;
import com.artivisi.snapsimulator.enums.VaStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record VirtualAccountView(String virtualAccountNo, String virtualAccountName, String trxId,
        BigDecimal totalAmount, String currency, Instant expiredDate, VaStatus status, Instant paidAt,
        Instant createdAt) {

    public static VirtualAccountView of(VirtualAccount va) {
        return new VirtualAccountView(va.getVirtualAccountNo(), va.getVirtualAccountName(), va.getTrxId(),
                va.getTotalAmount(), va.getCurrency(), va.getExpiredDate(), va.getStatus(), va.getPaidAt(),
                va.getCreatedAt());
    }
}
