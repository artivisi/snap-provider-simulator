package com.artivisi.snapsimulator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The bank's books: one credit per payment received, a debit when a credit is reversed. */
@Getter
@Setter
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    public static final String CREDIT = "CREDIT";
    public static final String DEBIT = "DEBIT";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id")
    private BankConnection connection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    private String journalId;
    private Instant transactionTime;
    private String entryType;
    private BigDecimal amount;
    private String currency;
    private String virtualAccountNo;
    private String remark;
}
