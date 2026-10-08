package com.artivisi.snapsimulator.dto.form;

import com.artivisi.snapsimulator.dto.EndpointRequest;
import com.artivisi.snapsimulator.entity.Partner;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EndpointForm {

    @NotBlank
    @Size(max = 500)
    @Pattern(regexp = "https?://\\S+", message = "must be an http or https URL")
    private String baseUrl;

    @NotBlank
    @Size(max = 128)
    private String clientId;

    @NotBlank
    @Size(max = 256)
    private String clientSecret;

    /** The stored secret is not shown again; the partner re-enters it on change. */
    public static EndpointForm of(Partner p) {
        EndpointForm form = new EndpointForm();
        form.setBaseUrl(p.getEndpointBaseUrl());
        form.setClientId(p.getEndpointClientId());
        return form;
    }

    public EndpointRequest toRequest() {
        return new EndpointRequest(baseUrl, clientId, clientSecret);
    }
}
