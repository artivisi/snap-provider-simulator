package com.artivisi.snapsimulator.dto.form;

import com.artivisi.snapsimulator.dto.SettingsRequest;
import com.artivisi.snapsimulator.dto.SignupRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupForm {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotNull
    @Min(SettingsRequest.MIN_TTL)
    @Max(SettingsRequest.MAX_TTL)
    private Integer tokenTtlSeconds;

    @NotNull(message = "choose on or off")
    private Boolean diagnosticMode;

    public SignupRequest toRequest() {
        return new SignupRequest(email, password, tokenTtlSeconds, diagnosticMode);
    }
}
