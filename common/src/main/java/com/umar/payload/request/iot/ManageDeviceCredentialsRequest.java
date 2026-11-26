package com.umar.payload.request.iot;

import com.umar.enums.iot.CredentialAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ManageDeviceCredentialsRequest {
    @NotNull
    private CredentialAction action;
    @NotBlank
    private String reasonCode;
    @NotBlank
    @Size(min = 10, max = 500)
    private String note;
}
