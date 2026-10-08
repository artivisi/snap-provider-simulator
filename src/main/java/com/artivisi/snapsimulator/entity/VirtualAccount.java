package com.artivisi.snapsimulator.entity;

import com.artivisi.snapsimulator.enums.VaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A bank-hosted VA created with create-va. */
@Getter
@Setter
@Entity
@Table(name = "virtual_account")
public class VirtualAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    private String customerNo;
    private String virtualAccountNo;
    private String virtualAccountName;
    private String virtualAccountEmail;
    private String virtualAccountPhone;
    private String trxId;
    private BigDecimal totalAmount;
    private String currency;
    private Instant expiredDate;
    @Enumerated(EnumType.STRING)
    private VaStatus status;
    /** billDetails, freeTexts, feeAmount, virtualAccountTrxType and additionalInfo as sent, echoed back. */
    @Column(name = "additional_json")
    private String extraJson;
    private Instant paidAt;
}
