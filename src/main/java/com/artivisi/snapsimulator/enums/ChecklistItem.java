package com.artivisi.snapsimulator.enums;

import com.artivisi.snapsimulator.entity.Partner;

import java.time.Instant;
import java.util.function.Function;

/** Onboarding steps, each stamped once with the time it first succeeded. */
public enum ChecklistItem {
    KEY_REGISTERED("Key registered", Partner::getKeyRegisteredAt),
    TOKEN_OBTAINED("Token obtained", Partner::getTokenObtainedAt),
    SIGNED_CALL("Signed call", Partner::getSignedCallAt),
    VA_CREATED("VA created", Partner::getVaCreatedAt),
    ENDPOINT_REACHABLE("Partner endpoint reachable", Partner::getEndpointReachableAt),
    INQUIRY_ANSWERED("Inquiry answered", Partner::getInquiryAnsweredAt),
    PAYMENT_ACKNOWLEDGED("Payment acknowledged", Partner::getPaymentAcknowledgedAt);

    private final String label;
    private final Function<Partner, Instant> reader;

    ChecklistItem(String label, Function<Partner, Instant> reader) {
        this.label = label;
        this.reader = reader;
    }

    public String label() {
        return label;
    }

    public Instant completedAt(Partner partner) {
        return reader.apply(partner);
    }
}
