package com.artivisi.snapsimulator.entity;

import com.artivisi.snapsimulator.enums.Bank;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A partner's registration with one bank: credentials, key, endpoint, settings and checklist. */
@Getter
@Setter
@Entity
@Table(name = "bank_connection")
public class BankConnection extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @Enumerated(EnumType.STRING)
    private Bank bank;
    /** 8 characters, left-padded with spaces (A17, A48). */
    private String partnerServiceId;
    private String clientId;
    private String clientSecret;
    private String publicKeyPem;
    private String endpointBaseUrl;
    private String endpointClientId;
    private String endpointClientSecret;
    private int tokenTtlSeconds;
    private boolean diagnosticMode;
    private Instant keyRegisteredAt;
    private Instant tokenObtainedAt;
    private Instant signedCallAt;
    private Instant vaCreatedAt;
    private Instant endpointReachableAt;
    private Instant inquiryAnsweredAt;
    private Instant paymentAcknowledgedAt;

    public boolean hasEndpoint() {
        return endpointBaseUrl != null;
    }

    /** partnerServiceId without the space padding: BCA's company code. */
    public String companyCode() {
        return partnerServiceId.trim();
    }

    /** Calls are refused when the account is disabled. */
    public boolean isEnabled() {
        return partner.isEnabled();
    }
}
