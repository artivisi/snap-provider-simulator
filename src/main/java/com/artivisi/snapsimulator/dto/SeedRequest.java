package com.artivisi.snapsimulator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Five VA numbers the partner app answers inquiries for, used in order for:
 * normal, normal, dropped notification, amount mismatch, duplicate notification.
 */
public record SeedRequest(
        @NotNull @Size(min = 5, max = 5) List<@NotBlank String> virtualAccountNos,
        @NotBlank @Pattern(regexp = "\\d{5}", message = "5 digits from the channel list") String channelId) {

    public SeedRequest {
        virtualAccountNos = virtualAccountNos == null ? null : List.copyOf(virtualAccountNos);
    }
}
