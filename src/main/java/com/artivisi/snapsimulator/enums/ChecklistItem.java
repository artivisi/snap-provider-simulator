package com.artivisi.snapsimulator.enums;

import com.artivisi.snapsimulator.entity.BankConnection;

import java.time.Instant;
import java.util.function.Function;

/** Onboarding steps, each stamped once with the time it first succeeded. */
public enum ChecklistItem {
    KEY_REGISTERED("Key registered", BankConnection::getKeyRegisteredAt),
    TOKEN_OBTAINED("Token obtained", BankConnection::getTokenObtainedAt),
    SIGNED_CALL("Signed call", BankConnection::getSignedCallAt),
    VA_CREATED("VA created", BankConnection::getVaCreatedAt),
    ENDPOINT_REACHABLE("Partner endpoint reachable", BankConnection::getEndpointReachableAt),
    INQUIRY_ANSWERED("Inquiry answered", BankConnection::getInquiryAnsweredAt),
    PAYMENT_ACKNOWLEDGED("Payment acknowledged", BankConnection::getPaymentAcknowledgedAt);

    private final String label;
    private final Function<BankConnection, Instant> reader;

    ChecklistItem(String label, Function<BankConnection, Instant> reader) {
        this.label = label;
        this.reader = reader;
    }

    public String label() {
        return label;
    }

    public Instant completedAt(BankConnection connection) {
        return reader.apply(connection);
    }
}
