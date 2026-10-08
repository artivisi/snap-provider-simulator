package com.artivisi.snapsimulator.entity;

import com.artivisi.snapsimulator.enums.NotificationStatus;
import com.artivisi.snapsimulator.enums.VaModel;
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

/** A customer payment the bank received, and the outcome of notifying the partner. */
@Getter
@Setter
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "virtual_account_id")
    private VirtualAccount virtualAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "va_model")
    private VaModel model;
    private String virtualAccountNo;
    private String trxId;
    private BigDecimal amount;
    private String currency;
    private String channelId;
    private String paymentRequestId;
    /** X-EXTERNAL-ID of the last payment call; a resend reuses it. */
    private String externalId;
    /** paidAmount sent in the notification, when it differs from the ledger (reconciliation scenarios). */
    private BigDecimal notifiedAmount;
    @Enumerated(EnumType.STRING)
    private NotificationStatus notificationStatus;
    /** Body of the payment call, kept so a resend sends exactly the same bytes. */
    private String notificationBody;
    private Instant paidAt;
}
