package com.artivisi.snapsimulator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** A customer paying a bank-hosted VA; the amount is the VA's total (closed payment). */
public record VaPayRequest(
        @NotBlank String virtualAccountNo,
        @NotBlank @Pattern(regexp = "\\d{5}", message = "5 digits from the channel list") String channelId) {
}
