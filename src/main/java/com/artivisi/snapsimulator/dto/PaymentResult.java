package com.artivisi.snapsimulator.dto;

import com.artivisi.snapsimulator.enums.NotificationStatus;

import java.util.UUID;

/**
 * Outcome of a simulated customer payment. paymentId is null when the flow
 * stopped before money moved (inquiry refused or partner unreachable).
 */
public record PaymentResult(UUID paymentId, String inquiryResponseCode, String paymentResponseCode,
        NotificationStatus notificationStatus, String message) {
}
