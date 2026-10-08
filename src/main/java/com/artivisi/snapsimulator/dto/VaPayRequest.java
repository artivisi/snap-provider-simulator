package com.artivisi.snapsimulator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** A customer paying a bank-hosted VA; the amount is the VA's total (closed payment). */
public record VaPayRequest(
        @NotBlank String virtualAccountNo,
        @NotBlank @Pattern(regexp = "\\d{4,5}", message = "a code from the bank's channel list") String channelId) {
}
