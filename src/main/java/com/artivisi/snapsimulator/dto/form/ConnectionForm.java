package com.artivisi.snapsimulator.dto.form;

import com.artivisi.snapsimulator.dto.ConnectionRequest;
import com.artivisi.snapsimulator.dto.SettingsRequest;
import com.artivisi.snapsimulator.enums.Bank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConnectionForm {

    @NotNull(message = "choose a bank")
    private Bank bank;

    @NotNull
    @Min(SettingsRequest.MIN_TTL)
    @Max(SettingsRequest.MAX_TTL)
    private Integer tokenTtlSeconds;

    @NotNull(message = "choose on or off")
    private Boolean diagnosticMode;

    public ConnectionRequest toRequest() {
        return new ConnectionRequest(bank, tokenTtlSeconds, diagnosticMode);
    }
}
