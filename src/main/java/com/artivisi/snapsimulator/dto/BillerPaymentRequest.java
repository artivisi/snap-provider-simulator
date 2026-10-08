package com.artivisi.snapsimulator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** A customer paying a biller-hosted VA at a bank channel. */
public record BillerPaymentRequest(
        @NotBlank @Pattern(regexp = " *\\d{1,8}\\d{1,13}", message = "VA prefix followed by 1-13 digits") String virtualAccountNo,
        @NotBlank @Pattern(regexp = "\\d{1,16}\\.\\d{2}", message = "decimal with 2 places, e.g. 150000.00") String amount,
        @NotBlank @Pattern(regexp = "\\d{5}", message = "5 digits from the channel list") String channelId) {
}
