package com.artivisi.snapsimulator.dto;

import com.artivisi.snapsimulator.enums.Bank;

import java.util.UUID;

/** Returned once, when a connection is created and on regeneration; the secret is not shown again. */
public record IssuedCredentials(UUID connectionId, Bank bank, String partnerServiceId, String clientId,
        String clientSecret) {
}
