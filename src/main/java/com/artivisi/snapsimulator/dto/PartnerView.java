package com.artivisi.snapsimulator.dto;

import com.artivisi.snapsimulator.entity.Partner;
import com.artivisi.snapsimulator.enums.ChecklistItem;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** What the portal API shows a partner about itself; never the client secret or endpoint secret. */
public record PartnerView(String email, String partnerServiceId, String clientId, boolean keyRegistered,
        String endpointBaseUrl, String endpointClientId, int tokenTtlSeconds, boolean diagnosticMode,
        List<Step> checklist) {

    public PartnerView {
        checklist = List.copyOf(checklist);
    }

    public record Step(ChecklistItem item, String label, Instant completedAt) {
    }

    public static List<Step> checklist(Partner p) {
        return Arrays.stream(ChecklistItem.values()).map(i -> new Step(i, i.label(), i.completedAt(p))).toList();
    }

    public static PartnerView of(Partner p) {
        return new PartnerView(p.getEmail(), p.getPartnerServiceId(), p.getClientId(), p.getPublicKeyPem() != null,
                p.getEndpointBaseUrl(), p.getEndpointClientId(), p.getTokenTtlSeconds(), p.isDiagnosticMode(),
                checklist(p));
    }
}
