package com.artivisi.snapsimulator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** A customer paying a biller-hosted VA at a bank channel. */
public record BillerPaymentRequest(
        @NotBlank @Pattern(regexp = " *\\d{2,26}", message = "VA prefix followed by the customer number") String virtualAccountNo,
        @NotBlank @Pattern(regexp = "\\d{1,16}\\.\\d{2}", message = "decimal with 2 places, e.g. 150000.00") String amount,
        @NotBlank @Pattern(regexp = "\\d{4,5}", message = "a code from the bank's channel list") String channelId) {
}
