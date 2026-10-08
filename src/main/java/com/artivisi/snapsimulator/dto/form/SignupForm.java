package com.artivisi.snapsimulator.dto.form;

import com.artivisi.snapsimulator.dto.SignupRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

    public SignupRequest toRequest() {
        return new SignupRequest(email, password);
    }
}
