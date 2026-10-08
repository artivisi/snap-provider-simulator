package com.artivisi.snapsimulator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "partner")
public class Partner extends BaseEntity {

    private String email;
    private String passwordHash;
    private boolean enabled;
    /** 8 characters, left-padded with spaces (A17). */
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
}
