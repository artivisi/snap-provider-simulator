package com.artivisi.snapsimulator.dto;

import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.enums.Bank;
import com.artivisi.snapsimulator.enums.ChecklistItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A connection as its partner sees it; never the client secret or the endpoint secret. */
public record ConnectionView(UUID id, Bank bank, String partnerServiceId, String clientId, boolean keyRegistered,
        String endpointBaseUrl, String endpointClientId, int tokenTtlSeconds, boolean diagnosticMode,
        boolean bankHostedVa, List<Step> checklist) {

    public ConnectionView {
        checklist = List.copyOf(checklist);
    }

    public record Step(ChecklistItem item, String label, Instant completedAt) {
    }

    public static ConnectionView of(BankConnection c, List<ChecklistItem> items, boolean bankHostedVa) {
        return new ConnectionView(c.getId(), c.getBank(), c.getPartnerServiceId(), c.getClientId(),
                c.getPublicKeyPem() != null, c.getEndpointBaseUrl(), c.getEndpointClientId(), c.getTokenTtlSeconds(),
                c.isDiagnosticMode(), bankHostedVa, items.stream().map(i -> new Step(i, i.label(), i.completedAt(c))).toList());
    }

    public long stepsDone() {
        return checklist.stream().filter(s -> s.completedAt() != null).count();
    }
}
