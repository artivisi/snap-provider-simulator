package com.artivisi.snapsimulator.dto.form;

import com.artivisi.snapsimulator.dto.SettingsRequest;
import com.artivisi.snapsimulator.entity.Partner;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SettingsForm {

    @NotNull
    @Min(SettingsRequest.MIN_TTL)
    @Max(SettingsRequest.MAX_TTL)
    private Integer tokenTtlSeconds;

    @NotNull(message = "choose on or off")
    private Boolean diagnosticMode;

    public static SettingsForm of(Partner p) {
        SettingsForm form = new SettingsForm();
        form.setTokenTtlSeconds(p.getTokenTtlSeconds());
        form.setDiagnosticMode(p.isDiagnosticMode());
        return form;
    }

    public SettingsRequest toRequest() {
        return new SettingsRequest(tokenTtlSeconds, diagnosticMode);
    }
}
